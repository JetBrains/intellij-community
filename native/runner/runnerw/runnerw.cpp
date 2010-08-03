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
		ErrorMessage("GenerateConsoleCtrlEvent");
	}
}

BOOL is_iac = FALSE;

char IAC = 5;
char BRK = 3;

BOOL Scan(char buf[], int count) {
	for (int i = 0; i < count; i++) {
		if (is_iac) {
			if (buf[i] == BRK) {
				CtrlBreak();
				return TRUE;
			} else {
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
	case CTRL_CLOSE_EVENT:
	case CTRL_LOGOFF_EVENT:
	case CTRL_SHUTDOWN_EVENT:
		CtrlBreak();
		return (TRUE);
	case CTRL_BREAK_EVENT:
		return FALSE;
	default:
		return FALSE;
	}
}

struct StdInThreadParams {
	HANDLE hEvent;
	HANDLE write_stdin;
};

DWORD WINAPI StdInThread(void *param) {
	StdInThreadParams *threadParams = (StdInThreadParams *) param;
	char buf[1];
	memset(buf, 0, sizeof(buf));

	HANDLE hStdin = GetStdHandle(STD_INPUT_HANDLE);
	while (true) {
		DWORD cbRead = 0;
		DWORD cbWrite = 0;

		char c;
		ReadFile(hStdin, &c, 1, &cbRead, NULL);
		if (cbRead > 0) {
			buf[0] = c;
			bool ctrlBroken = Scan(buf, 1);
			WriteFile(threadParams->write_stdin, buf, 1, &cbWrite, NULL);
			if (ctrlBroken) {
				SetEvent(threadParams->hEvent);
				break;
			}
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

	for (int i = 2; i < argc; i++) {
		args += " ";
		if (strchr(argv[i], ' ')) {
			args += "\"";
			args += argv[i];
			args += "\"";
		} else {
			args += argv[i];
		}
	}

	if (app.length() == 0) {
		PrintUsage();
	}

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

	char* c_app = new char[app.size() + 1];
	strcpy(c_app, app.c_str());

	if (hasEnding(app, std::string(".bat"))) {
		args = app + " " + args;
		c_app = NULL;
	}

	char* c_args = new char[args.size() + 1];
	strcpy(c_args, args.c_str());

	SetConsoleCtrlHandler((PHANDLER_ROUTINE) CtrlHandler, TRUE);

	if (!CreateProcess(c_app, // Application name
			c_args, // Application arguments
			NULL, NULL, TRUE, CREATE_DEFAULT_ERROR_MODE, NULL, NULL, &si, &pi)) {
		ErrorMessage("CreateProcess");
		CloseHandle(newstdin);
		CloseHandle(write_stdin);
		exit(0);
	}

	unsigned long exit = 0;
	unsigned long b_read;
	unsigned long avail;

	HANDLE threadEvent = CreateEvent(NULL, TRUE, FALSE, NULL);

	StdInThreadParams params;
	params.hEvent = threadEvent;
	params.write_stdin = write_stdin;

	CreateThread(NULL, 0, &StdInThread, &params, 0, NULL);

	HANDLE objects_to_wait[2];
	objects_to_wait[0] = threadEvent;
	objects_to_wait[1] = pi.hProcess;

	while (true) {
		int rc = WaitForMultipleObjects(2, objects_to_wait, FALSE, INFINITE);
		if (rc == WAIT_OBJECT_0 + 1) {
			break;
		}
	}

	GetExitCodeProcess(pi.hProcess, &exit);

	CloseHandle(pi.hThread);
	CloseHandle(pi.hProcess);
	CloseHandle(newstdin);
	CloseHandle(write_stdin);
	return exit;
}
