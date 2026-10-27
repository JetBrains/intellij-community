// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use std::ffi::c_void;
use std::path::{Path, PathBuf};
use std::sync::OnceLock;
use std::sync::atomic::{AtomicBool, Ordering};

use anyhow::{Context, Result, bail};

const PROPERTY: &str = "-Djdk.net.unixdomain.tmpdir";
const OPTION_VARIABLES: [&str; 3] = ["JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS"];
static DIRECTORY: OnceLock<PathBuf> = OnceLock::new();
static CLEANED: AtomicBool = AtomicBool::new(false);

pub(crate) fn prepare(main_class: &str, options: &mut Vec<String>) -> Result<()> {
    if main_class != "com.intellij.ls.server.MainImpl" || options.iter().any(|option| names_property(option)) {
        return Ok(());
    }
    for variable in OPTION_VARIABLES {
        if let Some(value) = std::env::var_os(variable)
            && has_property(&value.to_string_lossy())
        {
            return Ok(());
        }
    }
    let path = create_directory().context("Cannot prepare the Java socket directory")?;
    let option = format!("{PROPERTY}={}", path.display());
    if DIRECTORY.set(path.clone()).is_err() {
        let _ = std::fs::remove_dir_all(&path);
        bail!("The Java socket directory is already configured");
    }
    log::debug!("Java socket directory: {}", path.display());
    options.push(option);
    Ok(())
}

fn names_property(option: &str) -> bool {
    option == PROPERTY || option.strip_prefix(PROPERTY).is_some_and(|rest| rest.starts_with('='))
}

fn has_property(value: &str) -> bool {
    let mut quote = None;
    let mut token = String::new();
    for character in value.chars() {
        match character {
            '\'' | '"' if quote == Some(character) => quote = None,
            '\'' | '"' if quote.is_none() => quote = Some(character),
            character if character.is_whitespace() && quote.is_none() => {
                if names_property(&token) {
                    return true;
                }
                token.clear();
            }
            _ => token.push(character),
        }
    }
    names_property(&token)
}

pub(crate) fn owns_directory() -> bool {
    DIRECTORY.get().is_some()
}

pub(crate) fn cleanup() {
    if let Some(path) = DIRECTORY.get()
        && !CLEANED.swap(true, Ordering::AcqRel)
    {
        let _ = std::fs::remove_dir_all(path);
    }
}

pub(crate) extern "C" fn exit_hook(_status: i32) {
    cleanup();
}

#[repr(C)]
struct SecurityAttributes {
    length: u32,
    descriptor: *mut c_void,
    inherit: i32,
}

#[link(name = "kernel32")]
unsafe extern "system" {
    fn GetWindowsDirectoryW(buffer: *mut u16, size: u32) -> u32;
    fn GetCurrentProcess() -> *mut c_void;
    fn CloseHandle(handle: *mut c_void) -> i32;
    fn LocalFree(memory: *mut c_void) -> *mut c_void;
    fn CreateDirectoryW(path: *const u16, attributes: *const SecurityAttributes) -> i32;
}

#[link(name = "advapi32")]
unsafe extern "system" {
    fn OpenProcessToken(process: *mut c_void, access: u32, token: *mut *mut c_void) -> i32;
    fn GetTokenInformation(token: *mut c_void, class: u32, information: *mut c_void, length: u32, needed: *mut u32) -> i32;
    fn ConvertSidToStringSidW(sid: *mut c_void, string: *mut *mut u16) -> i32;
    fn ConvertStringSecurityDescriptorToSecurityDescriptorW(
        string: *const u16,
        revision: u32,
        descriptor: *mut *mut c_void,
        size: *mut u32,
    ) -> i32;
    #[link_name = "SystemFunction036"]
    fn random_bytes(buffer: *mut u8, length: u32) -> u8;
}

struct LocalMemory(*mut c_void);

impl Drop for LocalMemory {
    fn drop(&mut self) {
        // SAFETY: the conversion APIs allocate this memory with LocalAlloc.
        unsafe {
            let _ = LocalFree(self.0);
        }
    }
}

fn private_descriptor() -> Result<LocalMemory> {
    let mut token = std::ptr::null_mut();
    // SAFETY: the output points to a live handle slot; TOKEN_QUERY reads this process's token.
    if unsafe { OpenProcessToken(GetCurrentProcess(), 0x0008, &mut token) } == 0 {
        return Err(std::io::Error::last_os_error()).context("Cannot open the process token");
    }
    let mut needed = 0;
    // SAFETY: a null buffer requests the size of TokenUser, information class 1.
    unsafe {
        let _ = GetTokenInformation(token, 1, std::ptr::null_mut(), 0, &mut needed);
    }
    let mut information = vec![0usize; (needed as usize).div_ceil(size_of::<usize>())];
    // SAFETY: the buffer has the requested size and pointer alignment for TOKEN_USER.
    let read = unsafe { GetTokenInformation(token, 1, information.as_mut_ptr().cast(), needed, &mut needed) };
    let error = std::io::Error::last_os_error();
    // SAFETY: OpenProcessToken returned an owned handle.
    unsafe {
        let _ = CloseHandle(token);
    }
    if read == 0 {
        return Err(error).context("Cannot read the token user");
    }
    let sid = information[0] as *mut c_void;
    let mut sid_text = std::ptr::null_mut();
    // SAFETY: TOKEN_USER starts with SID_AND_ATTRIBUTES, whose first member is a SID pointer.
    if unsafe { ConvertSidToStringSidW(sid, &mut sid_text) } == 0 {
        return Err(std::io::Error::last_os_error()).context("Cannot format the token user SID");
    }
    let sid_memory = LocalMemory(sid_text.cast());
    let mut length = 0;
    // SAFETY: the API returned a null-terminated UTF-16 SID string.
    let sid_string = unsafe {
        while *sid_text.add(length) != 0 {
            length += 1;
        }
        String::from_utf16(std::slice::from_raw_parts(sid_text, length))?
    };
    drop(sid_memory);
    let sddl: Vec<u16> = format!("D:P(A;OICI;FA;;;{sid_string})").encode_utf16().chain(Some(0)).collect();
    let mut descriptor = std::ptr::null_mut();
    // SAFETY: the SDDL is terminated; the API returns an owned security descriptor.
    if unsafe { ConvertStringSecurityDescriptorToSecurityDescriptorW(sddl.as_ptr(), 1, &mut descriptor, std::ptr::null_mut()) } == 0 {
        return Err(std::io::Error::last_os_error()).context("Cannot create the private security descriptor");
    }
    Ok(LocalMemory(descriptor))
}

fn create_directory() -> Result<PathBuf> {
    let mut windows = vec![0u16; 32768];
    // SAFETY: the buffer has the stated capacity.
    let length = unsafe { GetWindowsDirectoryW(windows.as_mut_ptr(), windows.len() as u32) } as usize;
    if length == 0 || length >= windows.len() {
        bail!("Cannot locate the Windows directory: {}", std::io::Error::last_os_error());
    }
    let parent = PathBuf::from(String::from_utf16(&windows[..length])?).join("Temp");
    let descriptor = private_descriptor()?;
    for _ in 0..8 {
        let mut random = [0u8; 8];
        // SAFETY: the output buffer has the stated length.
        if unsafe { random_bytes(random.as_mut_ptr(), random.len() as u32) } == 0 {
            bail!("Cannot generate a Java socket directory name");
        }
        let name = format!("ij-{:016x}", u64::from_ne_bytes(random));
        let path = parent.join(name);
        match create_private_directory(&path, &descriptor) {
            Ok(()) => return Ok(path),
            Err(error)
                if error
                    .downcast_ref::<std::io::Error>()
                    .is_some_and(|error| error.raw_os_error() == Some(183)) =>
            {
                continue;
            }
            Err(error) => return Err(error),
        }
    }
    bail!("Cannot allocate a unique Java socket directory")
}

fn create_private_directory(path: &Path, descriptor: &LocalMemory) -> Result<()> {
    let text = path.to_str().context("The Java socket directory is not Unicode")?;
    if !text.is_ascii() || text.len() > 80 {
        bail!("The Java socket directory must have at most 80 ASCII bytes: {}", path.display());
    }
    let wide: Vec<u16> = text.encode_utf16().chain(Some(0)).collect();
    let attributes = SecurityAttributes {
        length: size_of::<SecurityAttributes>() as u32,
        descriptor: descriptor.0,
        inherit: 0,
    };
    // SAFETY: both the terminated path and security descriptor remain live through the call.
    if unsafe { CreateDirectoryW(wide.as_ptr(), &attributes) } == 0 {
        return Err(std::io::Error::last_os_error()).with_context(|| format!("Cannot create {}", path.display()));
    }
    Ok(())
}

#[cfg(test)]
mod tests;
