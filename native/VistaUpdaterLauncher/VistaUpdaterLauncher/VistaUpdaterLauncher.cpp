/*
* Copyright 2000-2009 JetBrains s.r.o.
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

#include "stdafx.h"
#include <windows.h>
#include <process.h>
#include <stdio.h>
#include <tchar.h>
#include <conio.h>
#include <string.h>
#include <stdlib.h>

static const wchar_t* INSTALL_PARAM = L"install";
static const wchar_t* SKIP_ELEVATION_PARAM = L"--skip-uac-elevation--";


bool isWritable(wchar_t* path)
{
	wprintf(L"Trying to create temporary file in\"%s\"\n", path);
	wchar_t fileName[32768] = L"";
	wcscat_s(fileName, path);
	wcscat_s(fileName, L"\\.jetbrains-uac-check");
	HANDLE file = CreateFile(fileName, GENERIC_READ|GENERIC_WRITE, 0, NULL, CREATE_NEW, 0, NULL);
	if (file == INVALID_HANDLE_VALUE)
	{
		DWORD error = GetLastError();
		if (error == ERROR_ACCESS_DENIED)
		{
			// looks like we need to request elevation
			return false;
		}
		else
		{
			// there's no need to request elevaion since patcher will most likely fail anyway
			wprintf(L"Unexpected error when creating temp file: %d\n", error);
			fflush(stdout);
			return true;
		}
	}

	CloseHandle(file);
	DeleteFile(fileName);
	return true;
}

void appendArgument(wchar_t result[], wchar_t argument[])
{
	bool needsQuoting = wcschr(argument, L' ') != NULL;
	if (needsQuoting)
	{
		wcscat_s(result, 32768, L"\"");
	}
	wcscat_s(result, 32768, argument);
	if (needsQuoting)
	{
		wcscat_s(result, 32768, L"\"");
	}
	wcscat_s(result, 32768, L" ");
}

bool getElevationPath(int argc, _TCHAR* argv[], wchar_t result[])
{
	int start = -1;
	for (int i = 1; i < argc; i++) {
		if (wcscmp(argv[i], SKIP_ELEVATION_PARAM) == 0)
		{
			wprintf(L"Elevation suppressed\n");
			fflush(stdout);
			return false;
		}

		if (wcscmp(argv[i], INSTALL_PARAM) == 0)
		{
			start = i;
		}
		else if (start >= 0)
		{
			if (i > start + 1)
			{
				wcscat_s(result, 32768, L" ");
			}
			wcscat_s(result, 32768, argv[i]);
		}
	}
	return start >= 0;
}

int _tmain(int argc, _TCHAR* argv[])
{
	wchar_t elevationPath[32768] = L"";
	HANDLE processHandle = NULL;
	if (getElevationPath(argc, argv, elevationPath) && !isWritable(elevationPath))
	{
		wchar_t paramsLine[32768] = L"";
		wcscat_s(paramsLine, SKIP_ELEVATION_PARAM);

		for (int i = 1; i < argc; i++) 
		{
			wcscat_s(paramsLine, L" ");
			appendArgument(paramsLine, argv[i]);
		}

		wprintf(L"Creating elevated process: %s %s\n", argv[0], paramsLine);
		fflush(stdout);

		SHELLEXECUTEINFO shExecInfo;
		shExecInfo.cbSize = sizeof(SHELLEXECUTEINFO);
		shExecInfo.fMask = SEE_MASK_NOCLOSEPROCESS;
		shExecInfo.hwnd = NULL;
		shExecInfo.lpVerb = L"runas";
		shExecInfo.lpFile = argv[0];
		shExecInfo.lpParameters = paramsLine;
		shExecInfo.lpDirectory = NULL;
		shExecInfo.nShow = SW_HIDE;
		shExecInfo.hInstApp = NULL;

		if (ShellExecuteEx(&shExecInfo) == FALSE)
		{
			wprintf(L"ShellExecuteEx() failed with error code %d\n", GetLastError());
			fflush(stdout);
			return -1;
		}
		processHandle = shExecInfo.hProcess;
	}
	else 
	{
		STARTUPINFO startupInfo = {0};
		startupInfo.cb = sizeof(startupInfo);
		PROCESS_INFORMATION processInformation = {0};

		startupInfo.hStdInput  = GetStdHandle(STD_INPUT_HANDLE);
		startupInfo.hStdOutput = GetStdHandle(STD_OUTPUT_HANDLE);
		startupInfo.hStdError  = GetStdHandle(STD_ERROR_HANDLE);

		wchar_t commandLine[32768] = L"";

		for (int i = 1; i < argc; i++) {
			if (wcscmp(argv[i], SKIP_ELEVATION_PARAM) != 0)
			{
				// add only original parameters
				appendArgument(commandLine, argv[i]);
			}
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
		processHandle = processInformation.hProcess;
	}

	WaitForSingleObject(processHandle, INFINITE);

	DWORD exitCode = 0;
	if (!GetExitCodeProcess(processHandle, &exitCode))
	{
		wprintf(L"Cannot retrieve process exit code: %d\n", GetLastError());
		exitCode = -1;
	}
	CloseHandle(processHandle);

	return exitCode;
}

