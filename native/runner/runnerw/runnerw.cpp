#include <windows.h>
#include <stdio.h>
#include <iostream>
#include <string>

void PrintUsageAndExit() {
	printf("Usage: runnerw.exe [/C] app [args]\n");
	printf("app [args]	Specifies executable file, arguments.\n");
	printf("/C	Creates a child process with new visible console.\n");
	printf("\n");
	printf("If '/C' option is specified, creates a child with a new visible console and attaches to this console.\n");
	printf("Otherwise, creates a child process with inherited input, output, and error streams.\n");
	printf("The input stream is scanned for the presence of the 2-char control sequences:\n");
	printf("  ENQ(5) and ETX(3) => a CTRL+BREAK signal is sent to the child process;\n");
	printf("  ENQ(5) and ENQ(5) => a CTRL+C signal is sent to the child process.\n");
	printf("Also in case of system shutdown a CTRL+BREAK signal is sent to the child process.\n");

	exit(0);
}

struct ChildParams {
	BOOL createNewConsole;
	LPWSTR commandLine;
};

void ErrorMessage(char *operationName) {
	LPWSTR msg;
	DWORD lastError = GetLastError();
	FormatMessageW(
		FORMAT_MESSAGE_ALLOCATE_BUFFER | FORMAT_MESSAGE_FROM_SYSTEM,
		NULL,
		lastError,
		MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT),
		(LPWSTR)&msg,
		0,
		NULL);
	if (msg) {
		fprintf(stderr, "runnerw.exe: %s failed with error %d: %ls\n", operationName, lastError, msg);
		LocalFree(msg);
	}
	else {
		fprintf(stderr, "runnerw.exe: %s failed with error %d (no message available)\n", operationName, lastError);
	}
	fflush(stderr);
}

void CtrlBreak() {
	if (!GenerateConsoleCtrlEvent(CTRL_BREAK_EVENT, 0)) {
		ErrorMessage("CtrlBreak(): GenerateConsoleCtrlEvent");
	}
}

void CtrlC() {
	if (!GenerateConsoleCtrlEvent(CTRL_C_EVENT, 0)) {
		ErrorMessage("CtrlC(): GenerateConsoleCtrlEvent");
	}
}

BOOL is_iac = FALSE;

char IAC = 5;
char BRK = 3;
char C = 5;

BOOL Scan(char buf[], int count) {
	for (int i = 0; i < count; i++) {
		if (is_iac) {
			if (buf[i] == BRK) {
				CtrlBreak();
				return TRUE;
			}
			else if (buf[i] == C) {
				CtrlC();
				return TRUE;
			}
			else {
				is_iac = FALSE;
			}
		}
		if (buf[i] == IAC) {
			is_iac = TRUE;
		}
	}

	return FALSE;
}

BOOL CtrlHandler(DWORD fdwCtrlType) {
	switch (fdwCtrlType) {
	case CTRL_C_EVENT:
		return TRUE;
	case CTRL_CLOSE_EVENT:
	case CTRL_LOGOFF_EVENT:
	case CTRL_SHUTDOWN_EVENT:
		CtrlBreak();
		return (TRUE);
	case CTRL_BREAK_EVENT:
		return TRUE;
	default:
		return FALSE;
	}
}

DWORD WINAPI scanStdinThread(void *param) {
	HANDLE *write_stdin = (HANDLE *) param;
	char buf[1];
	memset(buf, 0, sizeof(buf));

	HANDLE hStdin = GetStdHandle(STD_INPUT_HANDLE);
	BOOL endOfInput = false;
	while (!endOfInput) {
		DWORD nBytesRead = 0;
		DWORD nBytesWritten = 0;

		char c;
		BOOL bResult = ReadFile(hStdin, &c, 1, &nBytesRead, NULL);
		if (nBytesRead > 0) {
			buf[0] = c;
			BOOL ctrlBroken = Scan(buf, 1);
			WriteFile(*write_stdin, buf, 1, &nBytesWritten, NULL);
		}
		else {
			/*
			 When a synchronous read operation reaches the end of a file,
			 ReadFile returns TRUE and sets *lpNumberOfBytesRead to zero.
			 See http://msdn.microsoft.com/en-us/library/windows/desktop/aa365467(v=vs.85).aspx
			 */
			endOfInput = bResult;
		}
	}
	return 0;
}

BOOL attachChildConsole(PROCESS_INFORMATION const &childProcessInfo) {
	if (!FreeConsole()) {
		ErrorMessage("FreeConsole");
		return FALSE;
	}
	int attempts = 20;
	for (int i = 0; i < attempts; i++) {
		DWORD sleepMillis = i < 5 ? 30 : (i < 10 ? 100 : 500);
		// sleep to let child process initialize itself
		Sleep(sleepMillis);
		if (WaitForSingleObject(childProcessInfo.hProcess, 0) != WAIT_TIMEOUT) {
			// child process has been terminated, no console to attach to
			return FALSE;
		}
		if (AttachConsole(childProcessInfo.dwProcessId)) {
			return TRUE;
		}
	}
	ErrorMessage("AttachConsole");
	return FALSE;
}

BOOL isCommandLineMatchingArgs(LPWSTR commandLine, LPWSTR *expectedArgv, int expectedArgc) {
	int argc;
	LPWSTR *argv = CommandLineToArgvW(commandLine, &argc);
	if (argv == NULL) {
		return FALSE;
	}
	if (argc != expectedArgc) {
		LocalFree(argv);
		return FALSE;
	}
	for (int i = 0; i < argc; i++) {
		if (wcscmp(argv[i], expectedArgv[i]) != 0) {
			LocalFree(argv);
			return FALSE;
		}
	}
	LocalFree(argv);
	return TRUE;
}

LPWSTR copy(LPCWSTR str) {
	LPWSTR copy = _wcsdup(str);
	if (copy == NULL) {
		ErrorMessage("Storage cannot be allocated for string copy");
		exit(1);
	}
	return copy;
}

ChildParams parseChildParams() {
	LPWSTR commandLine = GetCommandLineW();
	int argc;
	LPWSTR *argv = CommandLineToArgvW(commandLine, &argc);
	if (argv == NULL) {
		PrintUsageAndExit();
	}
	if (argc <= 1) {
		LocalFree(argv);
		PrintUsageAndExit();
	}
	ChildParams result = {FALSE, NULL};
	result.createNewConsole = wcscmp(argv[1], L"/C") == 0 || wcscmp(argv[1], L"/c") == 0;
	int childArgvStartInd = result.createNewConsole ? 2 : 1;
	if (childArgvStartInd >= argc) {
		LocalFree(argv);
		PrintUsageAndExit();
	}
	std::wstring stdCmdLine(commandLine);
	for (size_t i = 0; i < stdCmdLine.length(); i++) {
		if (iswspace(stdCmdLine[i])) {
			LPWSTR subCmdLine = &commandLine[i + 1];
			if (isCommandLineMatchingArgs(subCmdLine, &argv[childArgvStartInd], argc - childArgvStartInd)) {
				result.commandLine = copy(subCmdLine);
				break;
			}
		}
	}
	LocalFree(argv);
	if (result.commandLine == NULL) {
		fwprintf(stderr, L"runnerw.exe: cannot determine child command line from its parent:\n%ls\n", commandLine);
		exit(1);
	}
	return result;
}

int main(int argc, char * argv[]) {
	ChildParams childParams = parseChildParams();

	STARTUPINFOW si;
	SECURITY_ATTRIBUTES sa;
	PROCESS_INFORMATION pi;

	HANDLE newstdin, write_stdin;

	sa.lpSecurityDescriptor = NULL;

	sa.nLength = sizeof(SECURITY_ATTRIBUTES);

	BOOL inheritHandles = !childParams.createNewConsole;
	sa.bInheritHandle = inheritHandles;

	if (!CreatePipe(&newstdin, &write_stdin, &sa, 0)) {
		ErrorMessage("CreatePipe");
		exit(0);
	}

	GetStartupInfoW(&si);

	DWORD processFlag = CREATE_DEFAULT_ERROR_MODE | CREATE_UNICODE_ENVIRONMENT;
	BOOL hasConsoleWindow = GetConsoleWindow() != NULL;
	if (childParams.createNewConsole) {
		processFlag |= CREATE_NEW_CONSOLE;
	}
	else if (hasConsoleWindow) {
		processFlag |= CREATE_NO_WINDOW;
	}

	if (inheritHandles) {
		si.dwFlags = STARTF_USESTDHANDLES;
		si.wShowWindow = SW_HIDE;
		si.hStdOutput = GetStdHandle(STD_OUTPUT_HANDLE);
		si.hStdError = GetStdHandle(STD_ERROR_HANDLE);
		si.hStdInput = newstdin;
	}

	if (childParams.createNewConsole) {
		si.lpTitle = childParams.commandLine;
	}

	if (!SetConsoleCtrlHandler(NULL, FALSE)) {
		ErrorMessage("Cannot restore normal processing of CTRL+C input");
	}

	if (!CreateProcessW(
			NULL,
			childParams.commandLine,
			NULL,
			NULL,
			inheritHandles,
			processFlag,
			NULL,
			NULL,
			&si,
			&pi)) {
		ErrorMessage("CreateProcess");
		CloseHandle(newstdin);
		CloseHandle(write_stdin);
		exit(0);
	}
	if (hasConsoleWindow || childParams.createNewConsole) {
		attachChildConsole(pi);
	}
	if (!SetConsoleCtrlHandler((PHANDLER_ROUTINE)CtrlHandler, TRUE)) {
		ErrorMessage("SetConsoleCtrlHandler");
	}

	CreateThread(NULL, 0, &scanStdinThread, &write_stdin, 0, NULL);

	unsigned long exitCode = 0;

	while (true) {
		int rc = WaitForSingleObject(pi.hProcess, INFINITE);
		if (rc == WAIT_OBJECT_0) {
			break;
		}
	}

	free(childParams.commandLine);

	GetExitCodeProcess(pi.hProcess, &exitCode);

	CloseHandle(pi.hThread);
	CloseHandle(pi.hProcess);
	CloseHandle(newstdin);
	CloseHandle(write_stdin);
	return exitCode;
}
