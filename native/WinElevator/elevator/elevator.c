/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <Windows.h>
#include <elevTools.h>
#include<stdio.h>
#include<io.h>
#include <fcntl.h>


// UAC-enabled (in manifset) tool to launch ptocesses.
// Connects to pipes and console, and then launches new process using CreateProcess

// Author: Ilya Kazakevich


// Connects and waits for pipe if required by descriptor flags
// nDescriptor ELEV_DESC_*
// nDescriptorFlags flags passed from launcher to check if descriptor should be connected
// Returns 0 if ok, error otherwise. Could be windows error, EBADF or EMFILE for dup2
static DWORD _ConnectIfNeededPipe(DWORD nParentPid, DWORD nDescriptor, FILE* stream, int nDescriptorFlags, _Out_ PHANDLE pRemoteProcessHandle)
{
	if (!(nDescriptorFlags & nDescriptor))
	{
		*pRemoteProcessHandle = GetStdHandle(ELEV_DESCR_GET_HANDLE(nDescriptor));
		return 0; // Not needed to connect pipe, use real descriptor
	}
	ELEV_PIPE_NAME sPipeName;
	ELEV_GEN_PIPE_NAME(sPipeName, nParentPid, nDescriptor);
	WaitNamedPipe(sPipeName, INFINITE);
	BOOL bStdIn = nDescriptor == ELEV_DESCR_STDIN;
	unsigned long access = (bStdIn ? GENERIC_READ : GENERIC_WRITE);
	HANDLE hPipe = CreateFile(sPipeName, access, 0, NULL, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, NULL);
	if (hPipe == INVALID_HANDLE_VALUE || hPipe == NULL)
	{
		return GetLastError();
	}

	// Make inheritable by remote process
	if (!SetHandleInformation(hPipe, HANDLE_FLAG_INHERIT, TRUE))
	{
		return GetLastError();
	}		

	// Fix Win32API
	DWORD hStdHandleToChange = ELEV_DESCR_GET_HANDLE(nDescriptor);
	if (!SetStdHandle(hStdHandleToChange, hPipe))
	{
		return GetLastError();
	}

	*pRemoteProcessHandle = hPipe;
	return 0;
}

// PID Directory DescriptorFlags ProgramToRun Arguments
#define _ARG_PID 1
#define _ARG_DIR 2
#define _ARG_DESCRIPTORS 3

int wmain(int argc, wchar_t* argv[], wchar_t* envp[])
{
	if (argc <= _ARG_DESCRIPTORS)
	{
		fwprintf(stderr, L"Bad command line");
		return -1;
	}
	if (!SetCurrentDirectory(argv[_ARG_DIR]))
	{
		fwprintf(stderr, L"Failed to set directory to %s : %ld", argv[_ARG_DIR], GetLastError());
		return -1;
	}
	DWORD nParentPid = _wtol(argv[_ARG_PID]);
	if (!nParentPid)
	{
		fwprintf(stderr, L"Failed to get parent pid from %s", argv[_ARG_PID]);
		return -1;
	}

	wchar_t* sDescriptorsStr = argv[_ARG_DESCRIPTORS];
	size_t nDescriptorsLen = wcslen(sDescriptorsStr);
	if (!nDescriptorsLen)
	{
		fwprintf(stderr, L"Failed to get descriptors from %s", sDescriptorsStr);
		return -1;
	}
	for(int i = 0; i < nDescriptorsLen; i++)
	{
		if (! iswdigit(sDescriptorsStr[i]))
		{
			fwprintf(stderr, L"Bad descriptor %s", sDescriptorsStr);
			return -1;
		}
	}
	int nDescriptorFlags = _wtoi(sDescriptorsStr);	

	


	wchar_t* sFromSeparator = wcsstr(GetCommandLine(), ELEV_COMMAND_LINE_SEPARATOR);
	if (! sFromSeparator)
	{
		fwprintf(stderr, L"Failed to find %s in %s", ELEV_COMMAND_LINE_SEPARATOR, GetCommandLine());
		return -1;
	}
	// Add rest commandline
	WCHAR* sCommandLine = sFromSeparator + wcslen(ELEV_COMMAND_LINE_SEPARATOR);

	// Fix console
	FreeConsole();
	if (!AttachConsole(nParentPid))
	{
		fwprintf(stderr, L"Failed to attach console: %d", GetLastError());
		return 1;
	}	
	
	STARTUPINFO startupInfo;
	ZeroMemory(&startupInfo, sizeof(startupInfo));
	startupInfo.cb = sizeof(startupInfo);
	startupInfo.dwFlags = STARTF_USESTDHANDLES; // To pass handles to remote process

	DWORD nError; // No place to output errors yet. Event log is overkill here, so we use  exit code. 	

	nError = _ConnectIfNeededPipe(nParentPid, ELEV_DESCR_STDIN, stdin, nDescriptorFlags, &startupInfo.hStdInput);
	if (nError != 0)
	{
		exit(nError);
	}
	nError = _ConnectIfNeededPipe(nParentPid, ELEV_DESCR_STDOUT, stdout, nDescriptorFlags, &startupInfo.hStdOutput);
	if (nError != 0)
	{
		exit(nError);
	}
	nError = _ConnectIfNeededPipe(nParentPid, ELEV_DESCR_STDERR, stderr, nDescriptorFlags, &startupInfo.hStdError);
	if (nError != 0)
	{
		exit(nError);
	}

	HANDLE parentProcess = OpenProcess(SYNCHRONIZE, FALSE, nParentPid);
	if (!parentProcess)
	{
		exit(GetLastError()); // If parent process can't be opened it probably dead
	}
	
	PROCESS_INFORMATION processInfo;
	
	if (!CreateProcess(NULL, sCommandLine, NULL, NULL, TRUE, NORMAL_PRIORITY_CLASS, NULL, NULL, &startupInfo, &processInfo))
	{
		fwprintf(stderr, L"Error launching process. Exit code %ld, command was %ls", GetLastError(), sCommandLine);
		return 1;
	}
	HANDLE processesToWait[] = { parentProcess, processInfo.hProcess };

	DWORD nWaitResult = WaitForMultipleObjects(2, processesToWait, FALSE, INFINITE);
	if (WAIT_FAILED == nWaitResult)
	{
		fwprintf(stderr, L"Error waiting processes: %ld", GetLastError());
		return -1;
	}
	if (nWaitResult - WAIT_OBJECT_0 == 0)
	{
		fwprintf(stderr, L"Parent process (launcher) died?");
		TerminateProcess(processInfo.hProcess, -1); 
		return -1;
	}

	
	
	DWORD nExitCode = 0;
	GetExitCodeProcess(processInfo.hProcess, &nExitCode);

	return nExitCode;
}
