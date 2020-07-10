#include <Windows.h>
#include <wchar.h>
#include <stdio.h>

// Accepts path to AppX reparse point in "%LOCALAPPDATA%\Microsoft\WindowsApps"
// Returns name of AppX or error if wrong reparse point

typedef struct
{
	ULONG ReparseTag;
	USHORT ReparseDataLength;
	USHORT Reserved;
	wchar_t DATA[1024]; //1024 wide chars should be enough for AppX name 
} REPARSE_DATA_BUFFER;

static DWORD _ProcessError(const wchar_t* place)
{
	const DWORD error = GetLastError();
	fwprintf(stderr, L"%ls: error %ld", place, error);
	return error;
}

int wmain(int argc, wchar_t* argv[], wchar_t* envp[])
{
	if (argc != 2)
	{
		fwprintf(stderr, L"Provide path to app");
		return -1;
	}
	const wchar_t* path = argv[1];

	HANDLE file = CreateFileW(
		path,
		GENERIC_READ,
		FILE_SHARE_READ,
		NULL,
		OPEN_EXISTING,
		FILE_FLAG_OPEN_REPARSE_POINT,
		NULL);
	if (file == INVALID_HANDLE_VALUE)
	{
		return _ProcessError(L"CreateFile");
	}

	REPARSE_DATA_BUFFER buffer;
	ZeroMemory(&buffer, sizeof(buffer));
	DWORD numOfBytes = 0;

	if (!DeviceIoControl(
		file,
		FSCTL_GET_REPARSE_POINT,
		NULL,
		0,
		&buffer,
		sizeof(buffer),
		&numOfBytes,
		NULL
	))
	{
		CloseHandle(file);
		return _ProcessError(L"DeviceIoCtl");
	}

	if (numOfBytes == 0 || buffer.ReparseTag != IO_REPARSE_TAG_APPEXECLINK)
	{
		fwprintf(stderr, L"Not a reparse point");
		CloseHandle(file);
		return -1;
	}


	const WCHAR* textStart = buffer.DATA;
	// Data consists of several unprintable (<32) chars following human-readable name of AppX
	// Search for first printable (>32) char.
	USHORT firstPrintableChar;
	const USHORT maxChars = buffer.ReparseDataLength / sizeof(WCHAR);
	for (firstPrintableChar = 0; (!iswprint(*textStart)) && firstPrintableChar < maxChars; firstPrintableChar++,
	     textStart++)
	{
	}
	if (firstPrintableChar == maxChars)
	{
		fwprintf(stderr, L"No printable chars in data");
		CloseHandle(file);
		return -1;
	}
	//stdlib wprintf will convert chars to 1-byte charset, which may break non-ascii chars (if any)
	WriteConsoleW(GetStdHandle(STD_OUTPUT_HANDLE), textStart, wcsnlen_s(textStart, maxChars - firstPrintableChar),
	              &numOfBytes, NULL);
	CloseHandle(file);
	return 0;
}
