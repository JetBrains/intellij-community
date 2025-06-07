// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use std::ffi::{c_char, c_int, c_void, CStr, CString};
use std::path::Path;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Mutex;
use std::thread;
use std::thread::JoinHandle;

use anyhow::{anyhow, bail, Context, Error, Result};
use jni::JNIEnv;
use jni::objects::{JObject, JValue};
use jni::sys::{jboolean, jint, jsize};
use log::{debug, error};

use crate::{jvm_property, ui};

#[cfg(target_os = "macos")]
use {
    core_foundation::base::{CFRelease, kCFAllocatorDefault, TCFTypeRef},
    core_foundation::date::CFTimeInterval,
    core_foundation::runloop::{CFRunLoopAddTimer, CFRunLoopGetCurrent, CFRunLoopRunInMode, CFRunLoopTimerCreate,
                               CFRunLoopTimerRef, kCFRunLoopDefaultMode, kCFRunLoopRunFinished}
};

#[cfg(target_os = "windows")]
const JVM_LIB_REL_PATH: &str = "bin\\server\\jvm.dll";
#[cfg(target_os = "macos")]
const JVM_LIB_REL_PATH: &str = "lib/libjli.dylib";
#[cfg(target_os = "linux")]
const JVM_LIB_REL_PATH: &str = "lib/server/libjvm.so";

static DEBUG_MODE: AtomicBool = AtomicBool::new(true);
static HOOK_MESSAGES: Mutex<Option<Vec<String>>> = Mutex::new(None);

#[no_mangle]
extern "C" fn vfprintf_hook(fp: *const c_void, format: *const c_char, args: va_list::VaList<'_>) -> jint {
    extern "C" {
        fn vfprintf(fp: *const c_void, format: *const c_char, args: va_list::VaList<'_>) -> c_int;
        fn vsnprintf(s: *mut c_char, n: usize, format: *const c_char, args: va_list::VaList<'_>) -> c_int;
    }

    match &mut *HOOK_MESSAGES.lock().unwrap() {
        None => unsafe { vfprintf(fp, format, args) },
        Some(messages) => {
            let mut buffer = [0; 4096];
            let len = unsafe { vsnprintf(buffer.as_mut_ptr(), buffer.len(), format, args) };
            let message = unsafe { CStr::from_ptr(buffer.as_ptr()) }.to_string_lossy().to_string();
            debug!("[JVM] vfprintf_hook: {:?}", message);
            messages.push(message);
            len
        }
    }
}

#[no_mangle]
extern "C" fn abort_hook() {
    error!("[JVM] abort_hook");
    match HOOK_MESSAGES.lock() {
        Ok(unlocked) => {
            let text = unlocked.as_ref().map(|lines| lines.join("")).unwrap_or("".to_string());
            if !text.is_empty() {
                let gui = !DEBUG_MODE.load(Ordering::Acquire);
                ui::show_error(gui, anyhow::format_err!(text))
            }
        }
        Err(e) => error!("[JVM] HOOK_MESSAGES.lock() failed: {}", e)
    }
}

const MAIN_METHOD_NAME: &str = "main";
const MAIN_METHOD_SIGNATURE: &str = "([Ljava/lang/String;)V";

type CreateJvmCall<'lib> = libloading::Symbol<
    'lib,
    unsafe extern "C" fn(*mut *mut jni::sys::JavaVM, *mut *mut c_void, *mut c_void) -> jint
>;

pub fn run_jvm_and_event_loop(jre_home: &Path, vm_options: Vec<String>, main_class: &str, args: Vec<String>, debug_mode: bool) -> Result<()> {
    debug!("Preparing a JVM environment");
    DEBUG_MODE.store(debug_mode, Ordering::Release);

    #[cfg(target_family = "unix")]
    {
        // resetting stack overflow protection handler set by the runtime (`std/src/sys/unix/stack_overflow.rs`)
        reset_signal_handler(libc::SIGBUS)?;
        reset_signal_handler(libc::SIGSEGV)?;
        // resetting interrupt handler masked when an IDE is launched in a particularly perverse way
        reset_signal_handler(libc::SIGINT)?;
    }

    let jre_home = jre_home.to_owned();
    let main_class = main_class.to_owned();
    let (tx, rx) = std::sync::mpsc::channel();

    // JNI docs say that JVM should not be created on primordial thread
    // (https://docs.oracle.com/en/java/javase/17/docs/specs/jni/invocation.html#creating-the-vm)
    debug!("Starting a JVM thread");
    let join_handle = thread::Builder::new().spawn(move || {
        debug!("[JVM] Thread started [{:?}]", thread::current().id());

        let mut vm_options = vm_options.clone();
        let mut java_command = main_class.clone();
        args.iter().for_each(|arg| {
            java_command += " ";
            java_command += arg
        });
        vm_options.push(jvm_property!("sun.java.command", java_command));

        let jni_env_result = load_and_start_jvm(&jre_home, vm_options);
        let jni_env = match jni_env_result {
            Ok(jni_env) => {
                tx.send(None).unwrap();
                jni_env
            }
            Err(e) => {
                tx.send(Some(e)).unwrap();
                return;
            }
        };

        match call_main_method(jni_env, &main_class, args) {
            Ok(_) => {
                debug!("[JVM] main method finished peacefully");
                std::process::exit(0);
            }
            Err(e) => {
                error!("[JVM] main method failed: {e:?}");
                std::process::exit(1);
            }
        };
    })?;

    debug!("Waiting for a JVM to be loaded");
    if let Some(e) = rx.recv()? {
        return Err(e);
    }

    // macOS reserves the primordial thread for the GUI event loop
    match run_event_loop(join_handle) {
        Ok(_) => Ok(()),
        Err(e) => Err(anyhow!("JVM thread panicked: {e:?}"))
    }
}

#[cfg(target_family = "unix")]
fn reset_signal_handler(signal: c_int) -> Result<()> {
    unsafe {
        let mut action: libc::sigaction = std::mem::zeroed();
        action.sa_sigaction = libc::SIG_DFL;
        match libc::sigaction(signal, &action, std::ptr::null_mut()) {
            0 => Ok(()),
            _ => bail!("sigaction({}): {}", signal, std::io::Error::last_os_error())
        }
    }
}

fn load_and_start_jvm(jre_home: &Path, vm_options: Vec<String>) -> Result<JNIEnv<'static>> {
    let libjvm_path = jre_home.join(JVM_LIB_REL_PATH);
    debug!("[JVM] Loading {libjvm_path:?}");
    let libjvm = load_libjvm(&libjvm_path)?;

    debug!("[JVM] Looking for 'JNI_CreateJavaVM' symbol");
    let create_jvm_call: CreateJvmCall<'_> = unsafe { libjvm.get(b"JNI_CreateJavaVM\0")? };

    debug!("[JVM] Constructing JVM init args");
    let mut java_vm: *mut jni::sys::JavaVM = std::ptr::null_mut();
    let mut jni_env: *mut jni::sys::JNIEnv = std::ptr::null_mut();
    let (jvm_init_args, jni_options) = get_jvm_init_args(vm_options)?;

    debug!("[JVM] Creating JVM");
    *HOOK_MESSAGES.lock()
        .map_err(|x| anyhow!("failed to acquire HOOK_MESSAGES mutex {x:?}"))? = Some(Vec::new());
    let create_jvm_result = unsafe {
        create_jvm_call(
            &mut java_vm as *mut *mut jni::sys::JavaVM,
            &mut jni_env as *mut *mut jni::sys::JNIEnv as *mut *mut c_void,
            &jvm_init_args as *const jni::sys::JavaVMInitArgs as *mut c_void)
    };
    debug!("[JVM] JNI_CreateJavaVM(): {}", create_jvm_result);

    release_jvm_init_args(jni_options);

    if create_jvm_result != jni::sys::JNI_OK {
        let text = HOOK_MESSAGES.lock()
            .map_err(|x| anyhow!("failed to acquire HOOK_MESSAGES mutex {x:?}"))?
            .as_ref()
            .context("failed to get as_ref from HOOK_MESSAGES.lock()")?.join("");
        bail!("{}", if text.is_empty() { format!("Unknown error (JNI_CreateJavaVM: {})", create_jvm_result) } else { text });
    }

    *HOOK_MESSAGES.lock()
        .map_err(|x| anyhow!("failed to acquire HOOK_MESSAGES mutex {x:?}"))? = None;

    let jni_env = unsafe { JNIEnv::from_raw(jni_env) }?;

    Ok(jni_env)
}

#[cfg(target_os = "windows")]
fn load_libjvm(libjvm_path: &Path) -> Result<libloading::Library> {
    unsafe { libloading::Library::new(libjvm_path) }
        .context("Failed to load 'jvm.dll'")
}

#[cfg(target_family = "unix")]
fn load_libjvm(libjvm_path: &Path) -> Result<libloading::Library> {
    let path_ref = Some(libjvm_path.as_os_str());
    let flags = libloading::os::unix::RTLD_LAZY;
    unsafe { libloading::os::unix::Library::open(path_ref, flags).map(From::from) }
        .with_context(|| format!("Failed to load '{}'", Path::new(libjvm_path.file_name().unwrap()).display()))
}

fn get_jvm_init_args(vm_options: Vec<String>) -> Result<(jni::sys::JavaVMInitArgs, Vec<jni::sys::JavaVMOption>)> {
    let mut jni_options = Vec::with_capacity(vm_options.len() + 1);

    jni_options.push(jni::sys::JavaVMOption {
        optionString: CString::new("abort")?.into_raw(),
        extraInfo: abort_hook as *mut c_void,
    });

    jni_options.push(jni::sys::JavaVMOption {
        optionString: CString::new("vfprintf")?.into_raw(),
        extraInfo: vfprintf_hook as *mut c_void,
    });

    let c_vm_options = convert_vm_options(vm_options)?;
    for opt in c_vm_options {
        jni_options.push(jni::sys::JavaVMOption {
            optionString: opt.into_raw(),
            extraInfo: std::ptr::null_mut(),
        });
    }

    let jvm_init_args = jni::sys::JavaVMInitArgs {
        version: jni::sys::JNI_VERSION_1_8,
        nOptions: jni_options.len() as jint,
        options: jni_options.as_ptr() as *mut jni::sys::JavaVMOption,
        ignoreUnrecognized: true as jboolean
    };

    Ok((jvm_init_args, jni_options))
}

#[cfg(target_os = "windows")]
fn convert_vm_options(vm_options: Vec<String>) -> Result<Vec<CString>> {
    use {
        windows::core::{BOOL, HSTRING, PCSTR},
        windows::Win32::Globalization::{GetACP, CP_ACP, CP_UTF8, WC_NO_BEST_FIT_CHARS, WideCharToMultiByte}
    };

    let mut strings = Vec::<CString>::with_capacity(vm_options.len());
    let acp = unsafe { GetACP() };
    debug!("[JVM] ACP={}", acp);

    for opt in vm_options {
        let str = if acp == CP_UTF8 {
            CString::new(opt.as_bytes())
        } else {
            let ucs_str = HSTRING::from(&opt);
            let mut acp_bytes = vec![0u8; ucs_str.len()];
            let mut failed = BOOL::default();
            let acp_len = unsafe {
                WideCharToMultiByte(CP_ACP, WC_NO_BEST_FIT_CHARS, &ucs_str, Some(&mut acp_bytes), PCSTR::null(), Some(&mut failed))
            };
            if acp_len == 0 {
                bail!("Cannot convert VM option string '{}' to ANSI code page ({}): {}", opt, acp, std::io::Error::last_os_error());
            }
            if failed.as_bool() {
                bail!("Cannot convert VM option string '{}' to ANSI code page ({})", opt, acp);
            }
            CString::new(acp_bytes)
        }.with_context(|| format!("Invalid VM option string: '{}'", opt))?;
        strings.push(str);
    }

    Ok(strings)
}

#[cfg(target_family = "unix")]
fn convert_vm_options(vm_options: Vec<String>) -> Result<Vec<CString>> {
    let mut strings = Vec::<CString>::with_capacity(vm_options.len());
    for opt in vm_options {
        strings.push(CString::new(opt.as_bytes())?);
    }
    Ok(strings)
}

fn release_jvm_init_args(jni_options: Vec<jni::sys::JavaVMOption>) {
    jni_options.into_iter().for_each(|option| drop(unsafe { CString::from_raw(option.optionString) }));
}

fn call_main_method(mut jni_env: JNIEnv<'_>, main_class: &str, args: Vec<String>) -> Result<()> {
    debug!("[JVM] Preparing args: {:?}", args);
    let main_class_name = main_class.replace('.', "/");
    let args_array = jni_env.new_object_array(args.len() as jsize, "java/lang/String", JObject::null())?;
    for (i, arg) in args.iter().enumerate() {
        jni_env.set_object_array_element(&args_array, i as jsize, jni_env.new_string(arg)?)?;
    }
    let main_args = vec![JValue::from(&args_array)];

    debug!("[JVM] Calling '{}#main'", main_class_name);
    match jni_env.call_static_method(main_class_name, MAIN_METHOD_NAME, MAIN_METHOD_SIGNATURE, &main_args) {
        Ok(_) => Ok(()),
        Err(e) => {
            if let jni::errors::Error::JavaException = e {
                jni_env.exception_describe()?
            };
            Err(Error::from(e))
        }
    }
}

#[cfg(target_os = "windows")]
fn run_event_loop(join_handle: JoinHandle<()>) -> thread::Result<()> {
    debug!("Joining the JVM thread");
    join_handle.join()
}

#[cfg(target_os = "macos")]
fn run_event_loop(_join_handle: JoinHandle<()>) -> thread::Result<()> {
    debug!("Running CoreFoundation event loop on primordial thread");

    extern "C" fn timer_empty(_timer: CFRunLoopTimerRef, _info: *mut c_void) {}

    unsafe {
        let timer = CFRunLoopTimerCreate(kCFAllocatorDefault, CFTimeInterval::MAX, 0.0, 0, 0, timer_empty, std::ptr::null_mut());
        CFRunLoopAddTimer(CFRunLoopGetCurrent(), timer, kCFRunLoopDefaultMode);
        CFRelease(timer.as_void_ptr());
    }

    loop {
        let result = unsafe { CFRunLoopRunInMode(kCFRunLoopDefaultMode, CFTimeInterval::MAX, 0) };
        if result == kCFRunLoopRunFinished {
            debug!("CoreFoundation event loop is finished");
            break;
        }
    }

    Ok(())
}

#[cfg(target_os = "linux")]
fn run_event_loop(join_handle: JoinHandle<()>) -> thread::Result<()> {
    debug!("Joining the JVM thread");
    join_handle.join()
}
