// restarter.cpp : Defines the entry point for the console application.
//

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

	int rc = _texecv(argv [2], argv+2);
	if (rc == -1)
	{
		_tprintf(_T("Error restarting process: errno is %d"), errno);
	}

	return 0;
}

