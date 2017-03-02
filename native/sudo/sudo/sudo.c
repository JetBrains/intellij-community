#include<stdio.h>
#include<Windows.h>
/*

Uses Windows Shell API to execute command with "runAs" verb which launches UAC window.
Result is stored in files and reported to caller *after* execution hence program must not be interactive.
Only batch style is supported.

Link CRT statically when building to prevent CRT redistribution (not an issue for Windows10+).

Author: Ilya Kazakevich
*/

// TODO: To support interactive, program must be rewritten to:
// Client.exe that creates named pipe and launches IntellijLauncher.exe with ShellExecute providing program name, pipe name and its pid
// IntellijLauncher.exe with UAC in manifest that connects to named pipe and runs program with CreateProcess providing pipes as handles and AttachConsole to pid
// IntellijLauncher.exe should also have signature to prevent yellow consent window


#define AS_BYTES(chars) (chars * sizeof(wchar_t))
// According to MSDN
#define MAX_COMMANDLINE_LENGTH 8191

// Write filePath to dst
void _outFile(wchar_t* filePath, FILE* dst) {
	HANDLE file = CreateFile(filePath, GENERIC_READ, 0, NULL, OPEN_EXISTING, 0, NULL);
	void* buffer;
	if (file) {
		DWORD size = GetFileSize(file, NULL);
		DWORD bytesRead;
		if (size) {
			buffer = malloc(size);
			ReadFile(file, buffer, size, &bytesRead, NULL);
			fwrite(buffer, 1, size, dst);
			free(buffer);
		}
	}
	CloseHandle(file);
}

int wmain(int argc, wchar_t *argv[]) {	
	size_t charsCommandLineSize = 0;
	wchar_t *commandLine = NULL, *commandToExecute;
	wchar_t comspec[MAX_PATH], tmpFolder[MAX_PATH], outName[MAX_PATH], errName[MAX_PATH];
	
	commandToExecute = calloc(MAX_COMMANDLINE_LENGTH, sizeof(wchar_t));

	GetEnvironmentVariable(L"ComSpec", comspec, MAX_PATH);

	GetTempPath(MAX_PATH, tmpFolder);

	if (wcslen(tmpFolder) + 14 > MAX_PATH) { //Tmp file is 14 chars long (see msdn)
		fprintf(stderr, "Temp folder is too long");
		return -1;
	}


	GetTempFileName(tmpFolder, L"out", 0, outName);
	GetTempFileName(tmpFolder, L"err", 0, errName);

	if (argc < 2) {
		fprintf(stderr, "Usage: sudo path_to_file.exe [argument1] [argument2] [argumentN]. Make sure your app is NOT interactive\n");
		return -1;
	}
	
	// Merge all arguments in one long string wrapping each in quotes and splitting with spaces
	for (int i = 1; i < argc; i++) {
		size_t charsLength = wcslen(argv[i]);
		size_t charsNewSize = charsCommandLineSize + charsLength +3 ;
		commandLine = realloc(commandLine, AS_BYTES(charsNewSize));
		commandLine[charsCommandLineSize] = L'"';
		memcpy(commandLine + charsCommandLineSize + 1, argv[i], AS_BYTES(charsLength));
		commandLine[charsNewSize - 2] = L'"';
		commandLine[charsNewSize - 1] = L' ';
		charsCommandLineSize = charsNewSize;
	}	
	commandLine[charsCommandLineSize] = 0x00;	

	wchar_t workingDir[MAX_PATH];
	GetCurrentDirectory(MAX_PATH, workingDir);
	// cmd /c [our_program] > tmp_out_name.txt 2> tmp_err_name.txt
	// cd /D is used to set working directory since lpDirectory does not work for ShellExecute + exe (file moniker does not support it?)
	// TODO: Use powershell and tee to redirect both to display and file (remove SW_HIDE then)
	swprintf(commandToExecute, AS_BYTES(MAX_COMMANDLINE_LENGTH), L"/C \"cd /D \"%ls\" && %ls > %ls  2> %ls\"", workingDir, commandLine, outName, errName);


	SHELLEXECUTEINFO execInfo;
	memset(&execInfo, 0, sizeof(SHELLEXECUTEINFO));
	execInfo.cbSize = sizeof(SHELLEXECUTEINFO);
	execInfo.fMask = SEE_MASK_NOASYNC | SEE_MASK_NOCLOSEPROCESS; // To block execution until external process ends
	execInfo.hwnd = NULL;
	execInfo.lpVerb = L"runAs"; //Run as administrator: semi-documented verb
	execInfo.lpFile = comspec;
	execInfo.lpParameters = commandToExecute;	
	execInfo.lpDirectory = workingDir;
	execInfo.nShow = SW_HIDE;
	execInfo.hInstApp = NULL;


	if (!ShellExecuteEx(&execInfo)) {
		DWORD error = GetLastError();
		fprintf(stderr, "Error: %ld", error);
		return error;
	}

	DWORD nExitCode;
	if (execInfo.hProcess) {
		WaitForSingleObject(execInfo.hProcess, INFINITE); //Wait for process
		GetExitCodeProcess(execInfo.hProcess, &nExitCode);
		CloseHandle(execInfo.hProcess);
	}

	// https://msdn.microsoft.com/en-us/library/windows/desktop/bb759784(v=vs.85).aspx
	int resultCode = (int)execInfo.hInstApp;
	if (resultCode < 32) {

		switch (resultCode) {
		case SE_ERR_FNF: fprintf(stderr, "File not found");
			break;
		case SE_ERR_PNF: fprintf(stderr, "Path not found");
			break;
		case SE_ERR_ACCESSDENIED: fprintf(stderr, "Access denied");
			break;
		default: fprintf(stderr, "Error code %d", resultCode);
		}
	}
	else {
		_outFile(outName, stdout);
		_outFile(errName, stderr);
	}
	
	
	DeleteFile(outName);
	DeleteFile(errName);
	free(commandToExecute);

	return nExitCode;

}