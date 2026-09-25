//! The liveness check of a process ID.

/// Tells if the process `pid` runs. On Unix this is `kill(pid, 0)`. A process of another user counts as gone, as in
/// Go. On Windows a process runs while it can be opened.
#[cfg(unix)]
pub(crate) fn runs(pid: u32) -> bool {
    let Ok(pid) = libc::pid_t::try_from(pid) else {
        return false;
    };
    // SAFETY: signal 0 only checks that the process exists and can receive a signal.
    pid > 0 && unsafe { libc::kill(pid, 0) } == 0
}

#[cfg(windows)]
pub(crate) fn runs(pid: u32) -> bool {
    use windows_sys::Win32::Foundation::CloseHandle;
    use windows_sys::Win32::System::Threading::{OpenProcess, PROCESS_QUERY_LIMITED_INFORMATION};
    // SAFETY: the call takes no pointer.
    let handle = unsafe { OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, 0, pid) };
    if handle.is_null() {
        return false;
    }
    // SAFETY: the handle is closed at once and used for nothing else.
    unsafe { CloseHandle(handle) };
    true
}
