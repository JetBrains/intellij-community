// VistaUpdaterLauncher.cpp : Defines the entry point for the console application.
//

#include "stdafx.h"
#include <windows.h>
#include <process.h>
#include <stdio.h>
#include <tchar.h>
#include <conio.h>
#include <string.h>
#include <stdlib.h>


int _tmain(int argc, _TCHAR* argv[])
{
	STARTUPINFO startupInfo = {0};
	startupInfo.cb = sizeof(startupInfo);
	PROCESS_INFORMATION processInformation = {0};

	startupInfo.hStdInput  = GetStdHandle(STD_INPUT_HANDLE);
	startupInfo.hStdOutput = GetStdHandle(STD_OUTPUT_HANDLE);
	startupInfo.hStdError  = GetStdHandle(STD_ERROR_HANDLE);

	wchar_t commandLine[32768] = L"";

	for (int i = 1; i < argc; i++) {
        wcscat_s(commandLine, L"\"");
        wcscat_s(commandLine, argv[i]);
        wcscat_s(commandLine, L"\" ");
	}

	wprintf(L"Creating new process: %s\n", commandLine);
	fflush(stdout);

	if (!CreateProcess(
		NULL, /*LPCTSTR lpApplicationName*/
		commandLine,/* LPTSTR lpCommandLine*/
		NULL, /*LPSECURITY_ATTRIBUTES lpProcessAttributes*/
		NULL, /*LPSECURITY_ATTRIBUTES lpThreadAttributes*/
		TRUE, /*BOOL bInheritHandles,*/
		0,    /*DWORD dwCreationFlags*/
		NULL, /*LPVOID lpEnvironment*/
		NULL, /*LPCTSTR lpCurrentDirectory*/
		&startupInfo, /*LPSTARTUPINFO lpStartupInfo*/
		&processInformation /*LPPROCESS_INFORMATION lpProcessInformation*/))
	{
			wprintf(L"Cannot create process: %d\n", GetLastError());
			return -1;
	}

	WaitForSingleObject(processInformation.hProcess, INFINITE);

	DWORD exitCode = 0;
	if (!GetExitCodeProcess(processInformation.hProcess, &exitCode))
	{
		wprintf(L"Cannot retrieve process exit code: %d\n", GetLastError());
		exitCode = -1;
	}
	CloseHandle(processInformation.hProcess);

	return exitCode;
}

