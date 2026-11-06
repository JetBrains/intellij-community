"""Run a command on a hidden desktop, so that its windows do not reach your screen.

The command and each process that it starts get real windows and a real event loop,
but no window reaches your screen. Your pointer, keyboard and focus stay free.

- Windows: the command runs on its own Windows desktop. java.awt.Robot input and
  screen captures do not reach that desktop. So a UI test must use posted input
  events and painted window images.
- Linux: the command runs on an Xvfb display, with a private session bus and
  without WAYLAND_DISPLAY. It needs xvfb-run, xauth and dbus-run-session.
- macOS: unsupported.

Usage:
  python hidden_desktop.py [--desktop NAME] [--timeout SECONDS] -- COMMAND [ARGS...]

A timeout or a signal makes the script kill COMMAND and each process of COMMAND.
When COMMAND exits, the script also kills each process that COMMAND left. On
Windows, the processes also stop when another process kills the script. On Linux,
a SIGKILL of the script leaves them. A process that leaves the job or the process
group stays, such as a Bazel server.

The exit code is the exit code of COMMAND, or one of these codes:
- 2: a wrong argument, or an unsupported OS.
- 124: the timeout stopped COMMAND.
- 125: the script cannot start COMMAND. On Linux, xvfb-run gives 127 instead when
  it cannot find COMMAND.
- 128 + N: the signal N stopped the script or COMMAND, for example 130 for Ctrl+C.

The script needs Python 3.8 or later and no packages.
"""

from __future__ import annotations

import argparse
import contextlib
import os
import shutil
import signal
import subprocess
import sys
import time
from collections.abc import Callable

if sys.platform not in ("win32", "linux"):
    print("hidden_desktop.py supports Windows and Linux only.", file=sys.stderr)
    sys.exit(2)

if sys.platform == "win32":
    import ctypes
    import msvcrt
    from ctypes import wintypes

    CREATE_SUSPENDED = 0x00000004
    GENERIC_ALL = 0x10000000
    # JobObjectExtendedLimitInformation of JOBOBJECTINFOCLASS
    EXTENDED_LIMIT_INFORMATION = 9
    JOB_OBJECT_LIMIT_BREAKAWAY_OK = 0x00000800
    JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE = 0x00002000
    # (DWORD) -1, the result of a failed ResumeThread
    RESUME_THREAD_FAILED = 0xFFFFFFFF
    STARTF_USESTDHANDLES = 0x00000100
    WAIT_TIMEOUT = 0x00000102

    user32 = ctypes.WinDLL("user32", use_last_error=True)
    kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)

    class STARTUPINFOW(ctypes.Structure):
        _fields_ = [
            ("cb", wintypes.DWORD),
            ("lpReserved", wintypes.LPWSTR),
            ("lpDesktop", wintypes.LPWSTR),
            ("lpTitle", wintypes.LPWSTR),
            ("dwX", wintypes.DWORD),
            ("dwY", wintypes.DWORD),
            ("dwXSize", wintypes.DWORD),
            ("dwYSize", wintypes.DWORD),
            ("dwXCountChars", wintypes.DWORD),
            ("dwYCountChars", wintypes.DWORD),
            ("dwFillAttribute", wintypes.DWORD),
            ("dwFlags", wintypes.DWORD),
            ("wShowWindow", wintypes.WORD),
            ("cbReserved2", wintypes.WORD),
            ("lpReserved2", ctypes.c_void_p),
            ("hStdInput", wintypes.HANDLE),
            ("hStdOutput", wintypes.HANDLE),
            ("hStdError", wintypes.HANDLE),
        ]

    class PROCESS_INFORMATION(ctypes.Structure):
        _fields_ = [
            ("hProcess", wintypes.HANDLE),
            ("hThread", wintypes.HANDLE),
            ("dwProcessId", wintypes.DWORD),
            ("dwThreadId", wintypes.DWORD),
        ]

    class JOBOBJECT_BASIC_LIMIT_INFORMATION(ctypes.Structure):
        _fields_ = [
            ("PerProcessUserTimeLimit", ctypes.c_int64),
            ("PerJobUserTimeLimit", ctypes.c_int64),
            ("LimitFlags", wintypes.DWORD),
            ("MinimumWorkingSetSize", ctypes.c_size_t),
            ("MaximumWorkingSetSize", ctypes.c_size_t),
            ("ActiveProcessLimit", wintypes.DWORD),
            ("Affinity", ctypes.c_size_t),
            ("PriorityClass", wintypes.DWORD),
            ("SchedulingClass", wintypes.DWORD),
        ]

    class JOBOBJECT_EXTENDED_LIMIT_INFORMATION(ctypes.Structure):
        _fields_ = [
            ("BasicLimitInformation", JOBOBJECT_BASIC_LIMIT_INFORMATION),
            # IO_COUNTERS has six ULONGLONG counters.
            ("IoInfo", ctypes.c_uint64 * 6),
            ("ProcessMemoryLimit", ctypes.c_size_t),
            ("JobMemoryLimit", ctypes.c_size_t),
            ("PeakProcessMemoryUsed", ctypes.c_size_t),
            ("PeakJobMemoryUsed", ctypes.c_size_t),
        ]

    user32.CreateDesktopW.restype = wintypes.HANDLE
    user32.CreateDesktopW.argtypes = [
        wintypes.LPCWSTR,
        wintypes.LPCWSTR,
        ctypes.c_void_p,
        wintypes.DWORD,
        wintypes.DWORD,
        ctypes.c_void_p,
    ]
    user32.CloseDesktop.argtypes = [wintypes.HANDLE]
    kernel32.CreateJobObjectW.restype = wintypes.HANDLE
    kernel32.CreateJobObjectW.argtypes = [ctypes.c_void_p, wintypes.LPCWSTR]
    kernel32.SetInformationJobObject.argtypes = [
        wintypes.HANDLE,
        ctypes.c_int,
        ctypes.c_void_p,
        wintypes.DWORD,
    ]
    kernel32.AssignProcessToJobObject.argtypes = [wintypes.HANDLE, wintypes.HANDLE]
    kernel32.TerminateJobObject.argtypes = [wintypes.HANDLE, wintypes.UINT]
    kernel32.TerminateProcess.argtypes = [wintypes.HANDLE, wintypes.UINT]
    kernel32.CreateProcessW.argtypes = [
        wintypes.LPCWSTR,
        wintypes.LPWSTR,
        ctypes.c_void_p,
        ctypes.c_void_p,
        wintypes.BOOL,
        wintypes.DWORD,
        ctypes.c_void_p,
        wintypes.LPCWSTR,
        ctypes.POINTER(STARTUPINFOW),
        ctypes.POINTER(PROCESS_INFORMATION),
    ]
    kernel32.ResumeThread.argtypes = [wintypes.HANDLE]
    kernel32.ResumeThread.restype = wintypes.DWORD
    kernel32.WaitForSingleObject.argtypes = [wintypes.HANDLE, wintypes.DWORD]
    kernel32.WaitForSingleObject.restype = wintypes.DWORD
    kernel32.GetExitCodeProcess.argtypes = [
        wintypes.HANDLE,
        ctypes.POINTER(wintypes.DWORD),
    ]
    kernel32.CloseHandle.argtypes = [wintypes.HANDLE]


class StartError(Exception):
    """The script cannot start the command."""


class Stopped(BaseException):
    """A signal stops the script, as KeyboardInterrupt does for SIGINT."""

    def __init__(self, signum: int) -> None:
        super().__init__(signum)
        self.signum = signum


def raise_stopped(signum: int, _frame: object) -> None:
    raise Stopped(signum)


def log(message: str) -> None:
    print(f"[hidden-desktop] {message}", file=sys.stderr, flush=True)


def supervise(
    pid: int,
    where: str,
    exited: Callable[[], bool],
    kill: Callable[[], object],
    timeout: float | None,
) -> int | None:
    """Waits until exited() is true, and returns None.

    On the timeout or a signal, calls kill() and returns 124 or 128 + the signal.
    """
    deadline = None if timeout is None else time.monotonic() + timeout
    try:
        log(f"started pid {pid} {where}")
        # Each exited() call waits at most 0.5 s, which keeps Ctrl+C responsive.
        while not exited():
            if deadline is not None and time.monotonic() > deadline:
                log(
                    f"timeout after {timeout:.0f} s: killing the processes of pid {pid}"
                )
                kill()
                return 124
    except (KeyboardInterrupt, Stopped) as stop:
        signum = stop.signum if isinstance(stop, Stopped) else signal.SIGINT
        name = signal.Signals(signum).name
        log(f"{name}: killing the processes of pid {pid}")
        kill()
        return 128 + signum
    return None


def win_error() -> str:
    """The text of the last Windows error."""
    return ctypes.WinError(ctypes.get_last_error()).strerror


def win_check(result: int, action: str) -> int:
    """Returns the result of a Win32 call. A zero result raises StartError."""
    if not result:
        raise StartError(f"cannot {action}: {win_error()}")
    return result


def inheritable_handle(stream):
    """The OS handle of a standard stream, made inheritable, or None without one."""
    try:
        handle = msvcrt.get_osfhandle(stream.fileno())
        os.set_handle_inheritable(handle, True)
        return handle
    except (OSError, ValueError, AttributeError):
        return None


def start_windows(command: list[str], desktop_name: str, cleanup):
    """Starts the command on the desktop in a new job. cleanup closes the handles."""
    # CreateDesktopW opens a desktop of the same name when it exists already.
    desktop = win_check(
        user32.CreateDesktopW(desktop_name, None, None, 0, GENERIC_ALL, None),
        f"create the desktop {desktop_name}",
    )
    cleanup.callback(user32.CloseDesktop, desktop)

    # Windows kills each process of the job when the last handle of the job closes.
    # This also occurs when another process kills this script.
    job = win_check(kernel32.CreateJobObjectW(None, None), "create a job object")
    cleanup.callback(kernel32.CloseHandle, job)
    limits = JOBOBJECT_EXTENDED_LIMIT_INFORMATION()
    # Without BREAKAWAY_OK, a process that asks to leave the job cannot start.
    limits.BasicLimitInformation.LimitFlags = (
        JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE | JOB_OBJECT_LIMIT_BREAKAWAY_OK
    )
    win_check(
        kernel32.SetInformationJobObject(
            job,
            EXTENDED_LIMIT_INFORMATION,
            ctypes.byref(limits),
            ctypes.sizeof(limits),
        ),
        "set the limits of the job object",
    )

    sys.stdout.flush()
    sys.stderr.flush()
    si = STARTUPINFOW(
        cb=ctypes.sizeof(STARTUPINFOW),
        # A name without a window station selects the window station of this process.
        lpDesktop=desktop_name,
        dwFlags=STARTF_USESTDHANDLES,
        hStdInput=inheritable_handle(sys.stdin),
        hStdOutput=inheritable_handle(sys.stdout),
        hStdError=inheritable_handle(sys.stderr),
    )
    pi = PROCESS_INFORMATION()
    cmdline = ctypes.create_unicode_buffer(subprocess.list2cmdline(command))
    # The process joins the job before it runs, so its child processes join too.
    win_check(
        kernel32.CreateProcessW(
            None,
            cmdline,
            None,
            None,
            True,
            CREATE_SUSPENDED,
            None,
            None,
            ctypes.byref(si),
            ctypes.byref(pi),
        ),
        f"start {command[0]}",
    )
    cleanup.callback(kernel32.CloseHandle, pi.hProcess)
    cleanup.callback(kernel32.CloseHandle, pi.hThread)
    try:
        win_check(
            kernel32.AssignProcessToJobObject(job, pi.hProcess),
            f"add pid {pi.dwProcessId} to the job object",
        )
    except StartError:
        kernel32.TerminateProcess(pi.hProcess, 1)
        raise
    if kernel32.ResumeThread(pi.hThread) == RESUME_THREAD_FAILED:
        raise StartError(f"cannot resume pid {pi.dwProcessId}: {win_error()}")
    return pi, job


def run_windows(command: list[str], desktop_name: str, timeout: float | None) -> int:
    with contextlib.ExitStack() as cleanup:
        try:
            pi, job = start_windows(command, desktop_name, cleanup)
        except StartError as error:
            log(str(error))
            return 125

        code = supervise(
            pi.dwProcessId,
            f"on the desktop {desktop_name}",
            exited=lambda: (
                kernel32.WaitForSingleObject(pi.hProcess, 500) != WAIT_TIMEOUT
            ),
            kill=lambda: kernel32.TerminateJobObject(job, 1),
            timeout=timeout,
        )
        if code is not None:
            return code

        exit_code = wintypes.DWORD()
        kernel32.GetExitCodeProcess(pi.hProcess, ctypes.byref(exit_code))
        log(f"pid {pi.dwProcessId} exited with code {exit_code.value}")
        # An NTSTATUS code such as 0xC0000005 overflows sys.exit
        # before Python 3.12.8 and 3.13.1.
        return ctypes.c_int32(exit_code.value).value


def run_linux(command: list[str], timeout: float | None) -> int:
    tools = ("xvfb-run", "xauth", "dbus-run-session")
    missing = [tool for tool in tools if shutil.which(tool) is None]
    if missing:
        log(
            f"cannot find {', '.join(missing)}: install the packages xvfb, xauth "
            "and dbus, or their equivalents"
        )
        return 125

    # Without WAYLAND_DISPLAY, the IDE cannot open a window on your Wayland session.
    env = {
        name: value for name, value in os.environ.items() if name != "WAYLAND_DISPLAY"
    }
    # Without a session bus, each IDE process starts a dbus-launch that outlives it.
    xvfb = ["xvfb-run", "-a", "-s", "-screen 0 1920x1080x24"]
    wrapped = ["dbus-run-session", "--", *xvfb, *command]
    try:
        # A new session keeps Ctrl+C away from the command.
        # The script then kills the whole process group.
        proc = subprocess.Popen(wrapped, env=env, start_new_session=True)
    except OSError as error:
        log(f"cannot start {wrapped[0]}: {error.strerror}")
        return 125
    # SIGTERM and SIGHUP stop the script as Ctrl+C does, so the script kills the group.
    for signum in (signal.SIGTERM, signal.SIGHUP):
        signal.signal(signum, raise_stopped)

    def exited() -> bool:
        try:
            proc.wait(0.5)
            return True
        except subprocess.TimeoutExpired:
            return False

    def kill() -> None:
        with contextlib.suppress(ProcessLookupError):
            os.killpg(proc.pid, signal.SIGKILL)
        proc.wait()

    code = supervise(proc.pid, "on an Xvfb display", exited, kill, timeout)
    if code is not None:
        return code
    # As the job object on Windows does, this stops each process that the command left.
    kill()

    log(f"pid {proc.pid} exited with code {proc.returncode}")
    # A negative return code is a signal. Shells report it as 128 + the signal number.
    return proc.returncode if proc.returncode >= 0 else 128 - proc.returncode


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Run a command on a hidden desktop, so that its windows "
        "do not reach your screen."
    )
    parser.add_argument(
        "--desktop",
        default="ide-tests",
        help="the name of the Windows desktop (default: ide-tests)",
    )
    parser.add_argument(
        "--timeout", type=float, help="kill the command after this many seconds"
    )
    parser.add_argument("command", nargs=argparse.REMAINDER)
    args = parser.parse_args()
    command = args.command[1:] if args.command[:1] == ["--"] else args.command
    if not command:
        parser.error("no command")

    if sys.platform == "win32":
        return run_windows(command, args.desktop, args.timeout)
    return run_linux(command, args.timeout)


if __name__ == "__main__":
    sys.exit(main())
