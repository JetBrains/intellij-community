// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

#include <stdarg.h>
#include <stdio.h>
#include <wchar.h>
#include <windows.h>


#define PROVIDER_NAME L"JB-Restarter"
#define ERR_OPEN_PROCESS (0xE0000000 + 100)
#define ERR_MAIN_WAIT_FAILED (0xE0000000 + 101)
#define ERR_ARGS (0xE0000000 + 102)
#define ERR_COMMAND_TOO_LONG (0xE0000000 + 110)
#define ERR_CREATE_PROCESS (0xE0000000 + 111)
#define ERR_COMMAND_WAIT_FAILED (0xE0000000 + 112)
#define ERR_GET_EXIT_CODE (0xE0000000 + 113)
#define WARN_COMMAND_FAILED (0x80000000 + 200)

#define COMMAND_SIZE 32768
#define MESSAGE_SIZE (COMMAND_SIZE + 1024)

static HANDLE event_log = NULL;
static void log_event(unsigned int event_id, const char *format, ...);

static void wait_for_parent(const wchar_t *pid_arg);
static void run_command(int cmd_argc, wchar_t *cmd_argv[], BOOL last);


int wmain(int argc, wchar_t *argv[]) {
  if (argc < 3) {
    printf("usage: %ls <pid> (n_args args ...)+\n", argv[0]);
    return 0;
  }

  event_log = RegisterEventSourceW(NULL, PROVIDER_NAME);

  wait_for_parent(argv[1]);

  int arg_index = 2;
  while (arg_index < argc - 1) {
    unsigned long cmd_argc = wcstoul(argv[arg_index++], NULL, 10);
    if (cmd_argc == 0 || cmd_argc > argc - arg_index) {
      log_event(ERR_ARGS, "unexpected '%s' @ %d", argv[arg_index - 1], arg_index - 1);
      break;
    } else {
      wchar_t **cmd_argv = argv + arg_index;
      arg_index += cmd_argc;
      run_command((int)cmd_argc, cmd_argv, arg_index >= argc - 1);
    }
  }

  if (event_log != NULL) {
    DeregisterEventSource(event_log);
  }

  return 0;
}


static void wait_for_parent(const wchar_t *pid_arg) {
  DWORD pid = wcstoul(pid_arg, NULL, 10);

  HANDLE parent = OpenProcess(SYNCHRONIZE, FALSE, pid);
  if (parent == NULL) {
    log_event(ERR_OPEN_PROCESS, "OpenProcess(%lu): %lu", pid, GetLastError());
    return;
  }

  DWORD res = WaitForSingleObject(parent, INFINITE);
  if (res != WAIT_OBJECT_0) {
    log_event(ERR_MAIN_WAIT_FAILED, "WaitForSingleObject: %08lX/%lu", res, GetLastError());
  }

  CloseHandle(parent);
}


static void run_command(int cmd_argc, wchar_t *cmd_argv[], BOOL last) {
  wchar_t cmd_line[COMMAND_SIZE], *p = cmd_line;
  for (int i = 0; i < cmd_argc; i++) {
    wchar_t *arg = cmd_argv[i];
    size_t arg_len = wcslen(arg);
    BOOL space = wcschr(arg, L' ') != NULL;
    if (space) *p++ = L'"';
    if (wcscpy_s(p, COMMAND_SIZE - (p - cmd_line), arg) != 0) {
      *p = L'\0';
      log_event(ERR_COMMAND_TOO_LONG, "[%ls %ls ...]", cmd_line, arg);
      return;
    } else {
      p += arg_len;
    }
    if (space) *p++ = L'"';
    *p++ = i < cmd_argc - 1 ? L' ' : L'\0';
  }

  STARTUPINFOW si = {0};
  PROCESS_INFORMATION pi = {0};
  if (!CreateProcessW(NULL, cmd_line, NULL, NULL, FALSE, 0, NULL, NULL, &si, &pi)) {
    log_event(ERR_CREATE_PROCESS, "CreateProcess(%ls): %lu", cmd_line, GetLastError());
    return;
  }

  if (!last) {
    DWORD res = WaitForSingleObject(pi.hProcess, INFINITE);
    if (res != WAIT_OBJECT_0) {
      log_event(ERR_COMMAND_WAIT_FAILED, "WaitForSingleObject: %08lX/%lu", res, GetLastError());
    }

    DWORD ec;
    if (!GetExitCodeProcess(pi.hProcess, &ec)) {
      log_event(ERR_GET_EXIT_CODE, "GetExitCode: %lu", GetLastError());
    } else if (ec != 0) {
      log_event(WARN_COMMAND_FAILED, "[%ls]: %lu", cmd_line, ec);
    }

    CloseHandle(pi.hProcess);
    CloseHandle(pi.hThread);
  }
}


static void log_event(unsigned int event_id, const char *format, ...) {
  va_list ap;
  va_start(ap, format);
  if (event_log != NULL) {
    char message[MESSAGE_SIZE];
    int n = vsnprintf_s(message, MESSAGE_SIZE, _TRUNCATE, format, ap);
    if (n > 0) {
      int severity = event_id >> 30;
      WORD type = (WORD)(severity == 3 ? EVENTLOG_ERROR_TYPE : severity == 2 ? EVENTLOG_WARNING_TYPE : EVENTLOG_INFORMATION_TYPE);
      ReportEventW(event_log, type, 0, event_id, NULL, 0, (DWORD)n, NULL, message);
    }
  }
  va_end(ap);
}