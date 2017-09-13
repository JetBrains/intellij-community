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
#include <stdio.h>

// Elevation "frontend". Launched by user it starts elevator, connects to it and reads data from it

// Author: Ilya Kazakevich


#define ERR_FAIL_READ -1
#define ERR_FAIL_WRITE -2
#define ERR_GET_DIR -3
#define ERR_LAUNCH -4
#define ERR_FAIL_WAIT -5
#define ERR_CREATE_PIPE -6

// Pipe that should be connected to remote process
typedef struct
{
	DWORD nRemoteProcessPid;
	DWORD nDescriptor; //One of ELEV_* descriptors
	BOOL bFromExternalProcess; // True if pipe is for READING from EXTERNAL process. Otherwise to write to it
} _PIPE_CONNECTION_INFO;


// Pipes to remote process. Accessed from another threads so they are global
static _PIPE_CONNECTION_INFO g_stdOutPipe, g_stdErrPipe, g_stdInPipe;


#define _CONFIGURE_PIPE_INFO(pipeInfo, nDescriptorToSet, nPid, bFromExternalProcessDirection) { \
		pipeInfo.nDescriptor = nDescriptorToSet; \
		pipeInfo.nRemoteProcessPid = nPid; \
		pipeInfo.bFromExternalProcess = bFromExternalProcessDirection; \
}

// Returns full command line excluding program itself
static WCHAR* _GetCommandLineNoProgram()
{
	WCHAR* sCommandLine = GetCommandLine();
	int nNumberOfArgs;
	WCHAR** args = CommandLineToArgvW(sCommandLine, &nNumberOfArgs);
	WCHAR* sProgram = args[0];
	size_t nProgramLengthChars = wcslen(sProgram);
	LocalFree(args);
	if (sCommandLine[0] == L'"')
	{
		nProgramLengthChars += 2; //Program name is in quotes
	}
	WCHAR * sCommandLineAfterProgram = sCommandLine + nProgramLengthChars;
	for (; sCommandLineAfterProgram[0] == L' '; sCommandLineAfterProgram++) {} // Remove spaces after program

	return sCommandLineAfterProgram;
}

// Adds argument to command line. 
// pchCurrentBufferSize should be *psCommandLine size. Incremeted automatically.
// psCommandLine to append to
// sStringToAdd arugment to add
// Escaping is not supported, so string can't have quotes
static void _AddStringToCommandLine(_Inout_ size_t* pchCurrentBufferChars, _Inout_ WCHAR** psCommandLine, _In_ WCHAR* sStringToAdd, _In_ BOOL bAddQuotes)
{
	// TODO: Doc suboptimal. Use line length instead of wcslen(*psCommandLine) ("shlemiel the painter algorithm")	

	size_t nCurrentLineLengthChars = wcslen(*psCommandLine);
	size_t nStringToAddLengthChars = wcslen(sStringToAdd);
	size_t nSpaceLeftInBufferChars = (*pchCurrentBufferChars) - nCurrentLineLengthChars;
	// "\"new_string_goes_here\" "
	size_t nRequiredSizeChars = (*pchCurrentBufferChars) + nStringToAddLengthChars;
	if (bAddQuotes)
	{
		nRequiredSizeChars += wcslen(L"\"\" ");
	}
	if (nSpaceLeftInBufferChars < nRequiredSizeChars)
	{
		// Not enough space, add more space
		(*pchCurrentBufferChars) = nRequiredSizeChars;
		*psCommandLine = realloc(*psCommandLine, sizeof(WCHAR) * (*pchCurrentBufferChars));
	}
	WCHAR* endOfCommandLine = (*psCommandLine) + nCurrentLineLengthChars;
	if (bAddQuotes) {
		wsprintf(endOfCommandLine, L"\"%ls\" ", sStringToAdd);
	}
	else
	{
		wcscat_s((*psCommandLine), (*pchCurrentBufferChars), sStringToAdd);
	}
}

// ThreadProc to connect pipe to remote process
static DWORD _CreateConnectPipe(_PIPE_CONNECTION_INFO* pPipeInfo)
{
	WCHAR pipeName[40];
	wsprintf(pipeName, L"JB-Launcher-Pipe-%ld", pPipeInfo->nDescriptor);
	HANDLE eventSource = RegisterEventSourceW(NULL, pipeName);


	ELEV_PIPE_NAME sPipeName;
	ELEV_GEN_PIPE_NAME(sPipeName, pPipeInfo->nRemoteProcessPid, pPipeInfo->nDescriptor);
	int access = (pPipeInfo->bFromExternalProcess ? PIPE_ACCESS_INBOUND : PIPE_ACCESS_OUTBOUND);
	HANDLE hExternalPipe = CreateNamedPipe(
		sPipeName,
		access,
		PIPE_READMODE_BYTE | PIPE_WAIT | PIPE_REJECT_REMOTE_CLIENTS | PIPE_TYPE_BYTE,
		1,
		ELEV_BUF_SIZE,
		ELEV_BUF_SIZE,
		0,
		NULL);
	if (hExternalPipe == NULL || hExternalPipe == INVALID_HANDLE_VALUE)
	{
		ReportEvent(eventSource, EVENTLOG_ERROR_TYPE, 0, ERR_CREATE_PIPE, NULL, 0, 0, NULL, NULL);
		fwprintf(stderr, L"Failed to create in pipe: %ld", GetLastError());		
		exit(ERR_CREATE_PIPE);
	}

	if (!ConnectNamedPipe(hExternalPipe, NULL))
	{
		ReportEvent(eventSource, EVENTLOG_ERROR_TYPE, 0, ERR_FAIL_WAIT, NULL, 0, 0, NULL, NULL);
		fwprintf(stderr, L"Failed to wait for in pipe: %ld", GetLastError());		
		exit(ERR_FAIL_WAIT);
	}


	char buffer[ELEV_BUF_SIZE];
	DWORD nBytesRead;
	DWORD nBytesWritten;
	HANDLE hInternal = GetStdHandle(ELEV_DESCR_GET_HANDLE(pPipeInfo->nDescriptor));
	HANDLE hToRead = (pPipeInfo->bFromExternalProcess ? hExternalPipe : hInternal);
	HANDLE hToWrite = ((! pPipeInfo->bFromExternalProcess) ? hExternalPipe : hInternal);

	while (1)
	{
		if (!ReadFile(hToRead, buffer, ELEV_BUF_SIZE, &nBytesRead, NULL))
		{
			DWORD nError = GetLastError();

			if (nError == ERROR_BROKEN_PIPE)
			{
				break;
			}
			ReportEvent(eventSource, EVENTLOG_ERROR_TYPE, 0, ERR_FAIL_READ, NULL, 0, 0, NULL, NULL);
			fwprintf(stderr, L"Failed to read from %ld: %ld", pPipeInfo->nDescriptor, GetLastError());
			exit(ERR_FAIL_READ);
		}
		if (!WriteFile(hToWrite, buffer, nBytesRead, &nBytesWritten, NULL))
		{
			DWORD nError = GetLastError();

			if ((!pPipeInfo->bFromExternalProcess) && nError == ERROR_BROKEN_PIPE)
			{				
				break;
			}
			ReportEvent(eventSource, EVENTLOG_WARNING_TYPE, 0, ERR_FAIL_WRITE, NULL, 0, 0, NULL, NULL);
			fwprintf(stderr, L"Failed to write: %ld", GetLastError());
			exit(ERR_FAIL_WRITE);
		}
		FlushFileBuffers(hToWrite);
	}
	CloseHandle(hToWrite);
	CloseHandle(hToRead);
	DeregisterEventSource(eventSource);
	return 0;
}

// If pipe is not console this function connects it, sets thread handler and configures descriptor flags to mark this descriptor is connected 
static void _LaunchPipeThread(_PIPE_CONNECTION_INFO* pPipeInfo, _Out_opt_ PHANDLE pThreadHandle, _Inout_ int* pDescriptorFlags)
{
	HANDLE hHandle = GetStdHandle(ELEV_DESCR_GET_HANDLE(pPipeInfo->nDescriptor));
	
	// Console apps may act differently if its stream is not connected to console, so we only connect when file or pipe is used
	if (GetFileType(hHandle) == FILE_TYPE_CHAR ) // Console (same as *nix istty()), do not touch
	{
		if (pThreadHandle) {
			*pThreadHandle = NULL;
		}
		return;
	}
	HANDLE hThread = CreateThread(NULL, 0, _CreateConnectPipe, pPipeInfo, 0, NULL);
	if (pThreadHandle)
	{
		*pThreadHandle = hThread;
	}
	*pDescriptorFlags |= pPipeInfo->nDescriptor;
}

int wmain(int argc, wchar_t* argv[], wchar_t* envp[])
{
	HANDLE eventSource = RegisterEventSourceW(NULL, L"JB-Launcher");

	
	DWORD nExitCode = 0;

	// Get pids
	DWORD nPid = GetCurrentProcessId();
	WCHAR sPid[20];
	_ltow_s(nPid, sPid, 20, 10);

	_CONFIGURE_PIPE_INFO(g_stdOutPipe, ELEV_DESCR_STDOUT, nPid, TRUE);
	_CONFIGURE_PIPE_INFO(g_stdErrPipe, ELEV_DESCR_STDERR, nPid, TRUE);
	_CONFIGURE_PIPE_INFO(g_stdInPipe, ELEV_DESCR_STDIN, nPid, FALSE);


	_mm_mfence(); // To make sure threads has access to g_

	HANDLE arHandlesToWait[] = {NULL, NULL};
	int nDescriptorFlags = 0;
	_LaunchPipeThread(&g_stdInPipe, NULL, &nDescriptorFlags); // No need to wait stdin thread so we do not need its handle
	_LaunchPipeThread(&g_stdOutPipe, &arHandlesToWait[0], &nDescriptorFlags);
	_LaunchPipeThread(&g_stdErrPipe, &arHandlesToWait[1], &nDescriptorFlags);

	
	//Get current dir
	WCHAR sCurrentDirectory[MAX_PATH + 1];
	GetCurrentDirectory(MAX_PATH, sCurrentDirectory);

	//Build commandline 
	size_t chCurrentSize = 1;
	WCHAR* sNewCommandLine = calloc(1, sizeof(WCHAR));

	_AddStringToCommandLine(&chCurrentSize, &sNewCommandLine, sPid, TRUE);
	_AddStringToCommandLine(&chCurrentSize, &sNewCommandLine, sCurrentDirectory, TRUE);
	WCHAR sDescriptorFlags[3];
	_itow_s(nDescriptorFlags, sDescriptorFlags, 2, 10);
	_AddStringToCommandLine(&chCurrentSize, &sNewCommandLine, sDescriptorFlags, TRUE);
	

	_AddStringToCommandLine(&chCurrentSize, &sNewCommandLine, ELEV_COMMAND_LINE_SEPARATOR, FALSE);

	
	// Add arguments provided by user to the tail of command line
	// https://blogs.msdn.microsoft.com/twistylittlepassagesallalike/2011/04/23/everyone-quotes-command-line-arguments-the-wrong-way/
	WCHAR * sOriginalCommandLineNoProgram = _GetCommandLineNoProgram();
	size_t nNewBufferSizeChars = wcslen(sOriginalCommandLineNoProgram) + wcslen(sNewCommandLine) + 1;
	sNewCommandLine = realloc(sNewCommandLine, nNewBufferSizeChars * sizeof(WCHAR));
	wcscat_s(sNewCommandLine, nNewBufferSizeChars, sOriginalCommandLineNoProgram);
		
	// Get full path to elevator
	WCHAR sPath[MAX_PATH + 1];
	if(!GetModuleFileName(NULL, sPath, MAX_PATH))
	{
		ReportEvent(eventSource, EVENTLOG_ERROR_TYPE, 0, ERR_GET_DIR, NULL, 0, 0, NULL, NULL);
		fprintf(stderr, "Failed to get directory: %ld", GetLastError());
		return ERR_GET_DIR;
	}

	WCHAR sDrive[_MAX_DRIVE], sDir[_MAX_DIR], sFile[_MAX_FNAME], sExt[_MAX_EXT];
	_wsplitpath_s(sPath, sDrive, _MAX_DRIVE, sDir, _MAX_DIR, sFile, _MAX_FNAME, sExt, _MAX_EXT);
	
	swprintf_s(sPath, MAX_PATH, L"%ls%lselevator.exe", sDrive, sDir);

	//Execute elevator
	SHELLEXECUTEINFO execInfo;
	ZeroMemory(&execInfo, sizeof(SHELLEXECUTEINFO));
	execInfo.cbSize = sizeof(SHELLEXECUTEINFO);
	execInfo.lpParameters = sNewCommandLine;
	execInfo.lpFile = sPath;
	execInfo.lpDirectory = sCurrentDirectory;
	execInfo.fMask = SEE_MASK_NOASYNC | SEE_MASK_NOCLOSEPROCESS | SEE_MASK_NO_CONSOLE;

	if (!ShellExecuteEx(&execInfo))
	{
		ReportEvent(eventSource, EVENTLOG_ERROR_TYPE, 0, ERR_LAUNCH, NULL, 0, 0, NULL, NULL);
		fprintf(stderr, "Failed to launch process: %ld", GetLastError());
		return ERR_LAUNCH;
	}
		
	// Wait for all threads
	for(int i = 0; i < 2; i++)
	{
		if (arHandlesToWait[i])
		{
			WaitForSingleObject(arHandlesToWait[i], INFINITE);
		}
	}

	// Process should be ended here, lets wait
	WaitForSingleObject(execInfo.hProcess, INFINITE);
	GetExitCodeProcess(execInfo.hProcess, &nExitCode);
	DeregisterEventSource(eventSource);
	return nExitCode;
}
