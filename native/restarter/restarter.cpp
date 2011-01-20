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


int _tmain(int argc, _TCHAR* argv[])
{
	if (argc < 3) return 0;
	int ppid = _ttoi(argv [1]);
	HANDLE parent_process = OpenProcess(SYNCHRONIZE, FALSE, ppid);
	if (parent_process)
	{
		WaitForSingleObject(parent_process, INFINITE);
		CloseHandle(parent_process);
	}

	int child_argc = argc-2;
	_TCHAR** child_argv = new _TCHAR* [child_argc+1];
	for(int i = 0; i < child_argc; i++)
	{
		if (_tcschr(argv[i+2], ' '))
		{
			int len = _tcslen(argv[i+2]) + 3;
			TCHAR *arg = new TCHAR[len];
			arg[0] = '\"';
			_tcscpy_s(arg+1, len, argv[i+2]);
			_tcscat_s(arg, len, _T("\""));
			child_argv[i] = arg;
		}
		else
		{
			child_argv[i] = argv[i+2];			
		}
	}
	child_argv[child_argc] = '\0';

	int rc = _texecv(argv [2], child_argv);
	if (rc == -1)
	{
		_tprintf(_T("Error restarting process: errno is %d"), errno);
	}

	return 0;
}

