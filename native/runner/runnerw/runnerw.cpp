#include <windows.h>
#include <stdio.h>
#include <tlhelp32.h>
#include <iostream>
#include <string>

void PrintUsage() {
	printf("Usage: runnerw.exe <app> <args>\n");
	printf("where <app> is console application and <args> it's arguments.\n");
	printf("\n");
	printf(
			"Runner invokes console application as a process with inherited input and output streams.\n");
	printf(
			"Input stream is scanned for presence of 2 char 255(IAC) and 243(BRK) sequence and generates Ctrl-Break event in that case.\n");
	printf(
			"Also in case of all type of event(Ctrl-C, Close, Shutdown etc) Ctrl-Break event is generated.\n");

	exit(0);
}

void ErrorMessage(char *str) {

	LPVOID msg;

	FormatMessage(FORMAT_MESSAGE_ALLOCATE_BUFFER | FORMAT_MESSAGE_FROM_SYSTEM,
			NULL, GetLastError(), MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT),
			(LPTSTR) &msg, 0, NULL);

	printf("%s: %s\n", str, msg);
	LocalFree(msg);
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

bool hasEnding(std::string const &fullString, std::string const &ending) {
	if (fullString.length() > ending.length()) {
		return (0 == fullString.compare(fullString.length() - ending.length(),
				ending.length(), ending));
	} else {
		return false;
	}
}

int main(int argc, char * argv[]) {
	if (argc < 2) {
		PrintUsage();
	}

	std::string app(argv[1]);
	std::string args("");

	for (int i = 1; i < argc; i++) {
                if (i>1) {
			args += " ";
		}
		if (strchr(argv[i], ' ')) {
			args += "\"";
			args += argv[i];
			args += "\"";
		} else {
			args += argv[i];
		}
	}

//	if (app.length() == 0) {
//		PrintUsage();
//	}

	STARTUPINFO si;
	SECURITY_ATTRIBUTES sa;
	PROCESS_INFORMATION pi;

	HANDLE newstdin, write_stdin;

	sa.lpSecurityDescriptor = NULL;

	sa.nLength = sizeof(SECURITY_ATTRIBUTES);
	sa.bInheritHandle = true;

	if (!CreatePipe(&newstdin, &write_stdin, &sa, 0)) {
		ErrorMessage("CreatePipe");
		exit(0);
	}

	GetStartupInfo(&si);

	si.dwFlags = STARTF_USESTDHANDLES;
	si.wShowWindow = SW_HIDE;
	si.hStdOutput = GetStdHandle(STD_OUTPUT_HANDLE);
	si.hStdError = GetStdHandle(STD_ERROR_HANDLE);
	si.hStdInput = newstdin;

	if (hasEnding(app, std::string(".bat"))) {
//              in MSDN it is said to do so, but actually that doesn't work
//		args = "/c " + args;
//		app = "cmd.exe";
	} else {
		app = "";
	}


	char* c_app = NULL;

 	if (app.size()>0) {
		c_app = new char[app.size() + 1];
		strcpy(c_app, app.c_str());
	}


	char* c_args = new char[args.size() + 1];
	strcpy(c_args, args.c_str());

	if (!SetConsoleCtrlHandler((PHANDLER_ROUTINE) CtrlHandler, TRUE)) {
		ErrorMessage("SetConsoleCtrlHandler");
	}

	if (!CreateProcess(c_app, // Application name
			c_args, // Application arguments
			NULL, NULL, TRUE, CREATE_DEFAULT_ERROR_MODE, NULL, NULL, &si, &pi)) {
		ErrorMessage("CreateProcess");
		CloseHandle(newstdin);
		CloseHandle(write_stdin);
		exit(0);
	}

	CreateThread(NULL, 0, &scanStdinThread, &write_stdin, 0, NULL);

	unsigned long exitCode = 0;

	while (true) {
		int rc = WaitForSingleObject(pi.hProcess, INFINITE);
		if (rc == WAIT_OBJECT_0) {
			break;
		}
	}

	GetExitCodeProcess(pi.hProcess, &exitCode);

	CloseHandle(pi.hThread);
	CloseHandle(pi.hProcess);
	CloseHandle(newstdin);
	CloseHandle(write_stdin);
	return exitCode;
}
