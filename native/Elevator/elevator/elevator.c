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
static void _ConnectIfNeededPipe(DWORD nParentPid, DWORD nDescriptor, FILE* stream, int nDescriptorFlags, _Out_ PHANDLE pRemoteProcessHandle)
{
	if (!(nDescriptorFlags & nDescriptor))
	{
		*pRemoteProcessHandle = GetStdHandle(ELEV_DESCR_GET_HANDLE(nDescriptor));
		return; // Not needed to connect pipe, use real descriptor
	}
	ELEV_PIPE_NAME sPipeName;
	ELEV_GEN_PIPE_NAME(sPipeName, nParentPid, nDescriptor);
	WaitNamedPipe(sPipeName, INFINITE);
	BOOL bStdIn = nDescriptor == ELEV_DESCR_STDIN;
	unsigned long access = (bStdIn ? GENERIC_READ : GENERIC_WRITE);
	HANDLE hPipe = CreateFile(sPipeName, access, 0, NULL, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, NULL);
	if (hPipe == INVALID_HANDLE_VALUE || hPipe == NULL)
	{
		DWORD nError = GetLastError();
		exit(nError); // No place to output errors yet		
	}

	// Make inheritable by remote process
	SetHandleInformation(hPipe, HANDLE_FLAG_INHERIT, TRUE);

	// Fix CRT
	_dup2(_open_osfhandle((intptr_t)hPipe, _O_WTEXT | _O_TEXT), _fileno(stream));

	// Fix Win32API
	DWORD hStdHandleToChange = ELEV_DESCR_GET_HANDLE(nDescriptor);
	SetStdHandle(hStdHandleToChange, hPipe);

	*pRemoteProcessHandle = hPipe;
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
		return 1;
	}
	SetCurrentDirectory(argv[_ARG_DIR]);
	DWORD nParentPid = _wtol(argv[_ARG_PID]);
	int nDescriptorFlags = _wtoi(argv[_ARG_DESCRIPTORS]);
		

	// Add rest commandline
	WCHAR* sCommandLine = wcsstr(GetCommandLine(), ELEV_COMMAND_LINE_SEPARATOR) + wcslen(ELEV_COMMAND_LINE_SEPARATOR);

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

	_ConnectIfNeededPipe(nParentPid, ELEV_DESCR_STDIN, stdin, nDescriptorFlags, &startupInfo.hStdInput);
	_ConnectIfNeededPipe(nParentPid, ELEV_DESCR_STDOUT, stdout, nDescriptorFlags, &startupInfo.hStdOutput);
	_ConnectIfNeededPipe(nParentPid, ELEV_DESCR_STDERR, stderr, nDescriptorFlags, &startupInfo.hStdError);

	HANDLE parentProcess = OpenProcess(SYNCHRONIZE, FALSE, nParentPid);
	
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
