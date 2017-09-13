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


#define ERR_INVALID_HANDLE -1
#define ERR_CANT_INHERIT -2
#define ERR_CANT_SET_CONSOLE -3
#define ERR_BAD_COMMAND_LINE -4
#define ERR_SET_DIR -5
#define ERR_PARENT_ID -6
#define ERR_GET_DESC -7
#define ERR_BAD_DESC -8
#define ERR_FAILED_TO_FIND -9 
#define ERR_FAILED_ATTACH - 10
#define ERR_OPEN_PARENT - 11
#define ERR_LAUNCHING - 12
#define ERR_WAITING - 13
#define ERR_PARENT_DIED -14


// UAC-enabled (in manifset) tool to launch ptocesses.
// Connects to pipes and console, and then launches new process using CreateProcess

// Author: Ilya Kazakevich


// Connects and waits for pipe if required by descriptor flags
// nDescriptor ELEV_DESC_*
// nDescriptorFlags flags passed from launcher to check if descriptor should be connected
// Returns 0 if ok, error otherwise. Could be windows error, EBADF or EMFILE for dup2
static DWORD _ConnectIfNeededPipe(DWORD nParentPid, DWORD nDescriptor, FILE* stream, int nDescriptorFlags, _Out_ PHANDLE pRemoteProcessHandle, _In_ HANDLE eventSource)
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
		ReportEvent(eventSource, EVENTLOG_ERROR_TYPE, 0, ERR_INVALID_HANDLE, NULL, 0, 0, NULL, NULL);
		return GetLastError();
	}

	// Make inheritable by remote process
	if (!SetHandleInformation(hPipe, HANDLE_FLAG_INHERIT, TRUE))
	{
		ReportEvent(eventSource, EVENTLOG_ERROR_TYPE, 0, ERR_CANT_INHERIT, NULL, 0, 0, NULL, NULL);
		return GetLastError();
	}		

	// Fix Win32API
	DWORD hStdHandleToChange = ELEV_DESCR_GET_HANDLE(nDescriptor);
	if (!SetStdHandle(hStdHandleToChange, hPipe))
	{
		ReportEvent(eventSource, EVENTLOG_ERROR_TYPE, 0, ERR_CANT_SET_CONSOLE, NULL, 0, 0, NULL, NULL);
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
	HANDLE eventSource = RegisterEventSourceW(NULL, L"JB-Elevator");

	if (argc <= _ARG_DESCRIPTORS)
	{
		ReportEvent(eventSource, EVENTLOG_ERROR_TYPE, 0, ERR_BAD_COMMAND_LINE, NULL, 0, 0, NULL, NULL);
		fwprintf(stderr, L"Bad command line");
		return ERR_BAD_COMMAND_LINE;
	}
	if (!SetCurrentDirectory(argv[_ARG_DIR]))
	{
		ReportEvent(eventSource, EVENTLOG_ERROR_TYPE, 0, ERR_SET_DIR, NULL, 0, 0, NULL, NULL);
		fwprintf(stderr, L"Failed to set directory to %s : %ld", argv[_ARG_DIR], GetLastError());
		return ERR_SET_DIR;
	}
	DWORD nParentPid = _wtol(argv[_ARG_PID]);
	if (!nParentPid)
	{
		ReportEvent(eventSource, EVENTLOG_ERROR_TYPE, 0, ERR_PARENT_ID, NULL, 0, 0, NULL, NULL);
		fwprintf(stderr, L"Failed to get parent pid from %s", argv[_ARG_PID]);
		return ERR_PARENT_ID;
	}

	wchar_t* sDescriptorsStr = argv[_ARG_DESCRIPTORS];
	size_t nDescriptorsLen = wcslen(sDescriptorsStr);
	if (!nDescriptorsLen)
	{
		ReportEvent(eventSource, EVENTLOG_ERROR_TYPE, 0, ERR_GET_DESC, NULL, 0, 0, NULL, NULL);
		fwprintf(stderr, L"Failed to get descriptors from %s", sDescriptorsStr);
		return ERR_GET_DESC;
	}
	for(int i = 0; i < nDescriptorsLen; i++)
	{
		if (! iswdigit(sDescriptorsStr[i]))
		{
			ReportEvent(eventSource, EVENTLOG_ERROR_TYPE, 0, ERR_BAD_DESC, NULL, 0, 0, NULL, NULL);
			fwprintf(stderr, L"Bad descriptor %s", sDescriptorsStr);
			return ERR_BAD_DESC;
		}
	}
	int nDescriptorFlags = _wtoi(sDescriptorsStr);	

	


	wchar_t* sFromSeparator = wcsstr(GetCommandLine(), ELEV_COMMAND_LINE_SEPARATOR);
	if (! sFromSeparator)
	{
		ReportEvent(eventSource, EVENTLOG_ERROR_TYPE, 0, ERR_FAILED_TO_FIND, NULL, 0, 0, NULL, NULL);
		fwprintf(stderr, L"Failed to find %s in %s", ELEV_COMMAND_LINE_SEPARATOR, GetCommandLine());
		return ERR_FAILED_TO_FIND;
	}
	// Add rest commandline
	WCHAR* sCommandLine = sFromSeparator + wcslen(ELEV_COMMAND_LINE_SEPARATOR);

	// Fix console
	FreeConsole();
	if (!AttachConsole(nParentPid))
	{
		ReportEvent(eventSource, EVENTLOG_ERROR_TYPE, 0, ERR_FAILED_ATTACH, NULL, 0, 0, NULL, NULL);
		fwprintf(stderr, L"Failed to attach console: %d", GetLastError());
		return ERR_FAILED_ATTACH;
	}	
	
	STARTUPINFO startupInfo;
	ZeroMemory(&startupInfo, sizeof(startupInfo));
	startupInfo.cb = sizeof(startupInfo);
	startupInfo.dwFlags = STARTF_USESTDHANDLES; // To pass handles to remote process

	DWORD nError; // No place to output errors yet. Event log is overkill here, so we use  exit code. 	

	nError = _ConnectIfNeededPipe(nParentPid, ELEV_DESCR_STDIN, stdin, nDescriptorFlags, &startupInfo.hStdInput, eventSource);
	if (nError != 0)
	{
		exit(nError);
	}
	nError = _ConnectIfNeededPipe(nParentPid, ELEV_DESCR_STDOUT, stdout, nDescriptorFlags, &startupInfo.hStdOutput, eventSource);
	if (nError != 0)
	{
		exit(nError);
	}
	nError = _ConnectIfNeededPipe(nParentPid, ELEV_DESCR_STDERR, stderr, nDescriptorFlags, &startupInfo.hStdError, eventSource);
	if (nError != 0)
	{
		exit(nError);
	}

	HANDLE parentProcess = OpenProcess(SYNCHRONIZE, FALSE, nParentPid);
	if (!parentProcess)
	{
		ReportEvent(eventSource, EVENTLOG_ERROR_TYPE, 0, ERR_OPEN_PARENT, NULL, 0, 0, NULL, NULL);
		exit(GetLastError()); // If parent process can't be opened it probably dead
	}
	
	PROCESS_INFORMATION processInfo;
	
	if (!CreateProcess(NULL, sCommandLine, NULL, NULL, TRUE, NORMAL_PRIORITY_CLASS, NULL, NULL, &startupInfo, &processInfo))
	{
		ReportEvent(eventSource, EVENTLOG_ERROR_TYPE, 0, ERR_LAUNCHING, NULL, 0, 0, NULL, NULL);
		fwprintf(stderr, L"Error launching process. Exit code %ld, command was %ls", GetLastError(), sCommandLine);
		return ERR_LAUNCHING;
	}
	HANDLE processesToWait[] = { parentProcess, processInfo.hProcess };

	DWORD nWaitResult = WaitForMultipleObjects(2, processesToWait, FALSE, INFINITE);
	if (WAIT_FAILED == nWaitResult)
	{
		ReportEvent(eventSource, EVENTLOG_ERROR_TYPE, 0, ERR_WAITING, NULL, 0, 0, NULL, NULL);
		fwprintf(stderr, L"Error waiting processes: %ld", GetLastError());
		return ERR_WAITING;
	}
	if (nWaitResult - WAIT_OBJECT_0 == 0)
	{
		ReportEvent(eventSource, EVENTLOG_ERROR_TYPE, 0, ERR_PARENT_DIED, NULL, 0, 0, NULL, NULL);
		fwprintf(stderr, L"Parent process (launcher) died?");
		TerminateProcess(processInfo.hProcess, -1); 
		return ERR_PARENT_DIED;
	}

	
	
	DWORD nExitCode = 0;
	GetExitCodeProcess(processInfo.hProcess, &nExitCode);
	DeregisterEventSource(eventSource);
	return nExitCode;
}
