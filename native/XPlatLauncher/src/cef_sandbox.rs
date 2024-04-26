// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

pub struct CefScopedSandboxInfo {
    pub ptr: *mut ::std::os::raw::c_void,
}

#[cfg(target_os = "windows")]
extern "C" {
    pub fn cef_sandbox_info_create() -> *mut ::std::os::raw::c_void;
    pub fn cef_sandbox_info_destroy(sandbox_info: *mut ::std::os::raw::c_void);
}

#[cfg(target_os = "windows")]
impl CefScopedSandboxInfo {
    pub fn new() -> CefScopedSandboxInfo {
        unsafe {
            CefScopedSandboxInfo {
                ptr: cef_sandbox_info_create(),
            }
        }
    }
}

#[cfg(target_os = "windows")]
impl Drop for CefScopedSandboxInfo {
    fn drop(&mut self) {
        unsafe {
            cef_sandbox_info_destroy(self.ptr);
        }
    }
}