#include <Windows.h>
#include <elevTools.h>
#include <stdio.h>

// Elevation "frontend". Launched by user it starts elevator, connects to it and reads data from it

// Author: Ilya Kazakevich

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

// ThreadProc to connect pipe to remote process
static DWORD _CreateConnectPipe(_PIPE_CONNECTION_INFO* pPipeInfo)
{
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
		fwprintf(stderr, L"Failed to create in pipe: %ld", GetLastError());
		exit(-1);
	}

	if (!ConnectNamedPipe(hExternalPipe, NULL))
	{
		fwprintf(stderr, L"Failed to wait for in pipe: %ld", GetLastError());
		exit(-1);
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
			fwprintf(stderr, L"Failed to read from %ld: %ld", pPipeInfo->nDescriptor, GetLastError());
			exit(-1);
		}
		if (!WriteFile(hToWrite, buffer, nBytesRead, &nBytesWritten, NULL))
		{
			DWORD nError = GetLastError();

			if ((!pPipeInfo->bFromExternalProcess) && nError == ERROR_BROKEN_PIPE)
			{				
				break;
			}
			fwprintf(stderr, L"Failed to write: %ld", GetLastError());
			exit(-1);
		}
		FlushFileBuffers(hToWrite);
	}
	CloseHandle(hToWrite);
	CloseHandle(hToRead);
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

static void _AddRestOfCommandLine(WCHAR** psNewCommandLine)
{
	// https://blogs.msdn.microsoft.com/twistylittlepassagesallalike/2011/04/23/everyone-quotes-command-line-arguments-the-wrong-way/
	WCHAR* sOriginalCommandLine = GetCommandLine();
	int nNumberOfOriginalArgs;
	WCHAR** sOriginalArgs = CommandLineToArgvW(sOriginalCommandLine, &nNumberOfOriginalArgs);
	size_t nCommandSizeChars = wcslen(sOriginalArgs[0]);
	WCHAR* sOriginalCommandLineNoCommand = (sOriginalCommandLine + nCommandSizeChars);
	size_t nNewBufferSizeChars = (wcslen(*psNewCommandLine) + wcslen(sOriginalCommandLineNoCommand) + 1);
	*psNewCommandLine = realloc(*psNewCommandLine, nNewBufferSizeChars * sizeof(WCHAR));
	wcscat_s(*psNewCommandLine, nNewBufferSizeChars, sOriginalCommandLineNoCommand);
}

int wmain(int argc, wchar_t* argv[], wchar_t* envp[])
{

	
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

	ElevAddStringToCommandLine(&chCurrentSize, &sNewCommandLine, sPid);
	ElevAddStringToCommandLine(&chCurrentSize, &sNewCommandLine, sCurrentDirectory);
	WCHAR sDescriptorFlags[3];
	_itow_s(nDescriptorFlags, sDescriptorFlags, 2, 10);
	ElevAddStringToCommandLine(&chCurrentSize, &sNewCommandLine, sDescriptorFlags);
	

	// Add arguments provided by user to the tail of command line
	_AddRestOfCommandLine(&sNewCommandLine);
	
	// Get full path to elevator
	WCHAR sPath[MAX_PATH + 1];
	if(!GetModuleFileName(NULL, sPath, MAX_PATH))
	{
		fprintf(stderr, "Failed to get directory: %ld", GetLastError());
		return 1;
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
		fprintf(stderr, "Failed to launch process: %ld", GetLastError());
		return 1;
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
	return nExitCode;
}
