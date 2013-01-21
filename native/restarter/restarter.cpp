/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
#include <process.h>

_TCHAR **convert_args(int argc, _TCHAR **argv) {
	_TCHAR** result = new _TCHAR* [argc+1];
	for(int i = 0; i < argc; i++)
	{
		if (_tcschr(argv[i], ' '))
		{
			int len = _tcslen(argv[i]) + 3;
			TCHAR *arg = new TCHAR[len];
			arg[0] = '\"';
			_tcscpy_s(arg+1, len, argv[i]);
			_tcscat_s(arg, len, _T("\""));
			result[i] = arg;
		}
		else
		{
			result[i] = argv[i];			
		}
	}
	result[argc] = '\0';
	return result;
}

// usage "pid_to_wait (commands_num commands...)*
int _tmain(int argc, _TCHAR* argv[])
{
	if (argc < 3) return 0;

	int arg_index = 1;

	int ppid = _ttoi(argv [arg_index++]);
	HANDLE parent_process = OpenProcess(SYNCHRONIZE, FALSE, ppid);
	if (parent_process)
	{
		WaitForSingleObject(parent_process, INFINITE);
		CloseHandle(parent_process);
	}

	FILE *file = 0;
#ifdef _DEBUG
	file = fopen("c:/restarter_debug.txt", "w");
#endif

	while (arg_index < argc - 1) {
		int argc_to_perform = _ttoi(argv[arg_index++]);
		if (argc_to_perform > 0) {
			_TCHAR *command_to_perform = argv[arg_index];
			_TCHAR **argv_to_perform = convert_args(argc_to_perform, &argv[arg_index]);
			arg_index += argc_to_perform;

			if (file) {
				_fwprintf_p(file, L"=================\n");
				_fwprintf_p(file, L"argc:%d\n", argc_to_perform);
				for(int ii = 0; ii < argc_to_perform; ii++) {
					_fwprintf_p(file, L"  %s\n", argv_to_perform[ii]);
				}
				fflush(file);
			}

			int last = arg_index >= argc - 1; 
			int rc = _tspawnv(last ? _P_NOWAIT : _P_WAIT, command_to_perform, argv_to_perform);
			if (file && rc == -1) {
				_fwprintf_p(file, L"Error restarting process: errno is %d\n", errno);
				fflush(file);
			}
		}
	}

	if (file) {
		_fwprintf_p(file, L"Finished\n");
		fclose(file);
	}
	return 0;
}
