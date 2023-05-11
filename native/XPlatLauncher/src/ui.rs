// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

#[cfg(target_os = "windows")]
use {
    std::ffi::CString,
    windows::core::PCSTR,
    windows::Win32::Foundation::HWND,
    windows::Win32::UI::WindowsAndMessaging
};

#[cfg(target_os = "macos")]
use {
    core_foundation::base::{CFOptionFlags, SInt32, TCFType},
    core_foundation::date::CFTimeInterval,
    core_foundation::string::{CFString, CFStringRef},
    core_foundation::url::CFURLRef
};

#[cfg(not(any(target_os = "macos", target_os = "windows")))]
use {
    log::error,
    native_dialog::{MessageDialog, MessageType}
};

const ERROR_TITLE: &str = "Cannot start the IDE";
const ERROR_FOOTER: &str =
    "Please try to reinstall the IDE.\n\
    For support, please refer to https://jb.gg/ide/critical-startup-errors";

pub fn show_error(gui: bool, error: anyhow::Error) {
    if gui {
        show_alert_impl(ERROR_TITLE, &format!("{:?}\n\n{}", error, ERROR_FOOTER));
    } else {
        eprintln!("\n=== {ERROR_TITLE} ===\n{error:?}");
    }
}

#[cfg(target_os = "windows")]
#[allow(unused_results)]
fn show_alert_impl(title: &str, text: &str) {
    let c_caption = CString::new(title).unwrap();
    let c_text = CString::new(text).unwrap();
    unsafe {
        WindowsAndMessaging::MessageBoxA(
            HWND::default(),
            PCSTR::from_raw(c_text.as_bytes_with_nul().as_ptr()),
            PCSTR::from_raw(c_caption.as_bytes_with_nul().as_ptr()),
            WindowsAndMessaging::MB_OK | WindowsAndMessaging::MB_ICONERROR | WindowsAndMessaging::MB_APPLMODAL);
    }
}

#[cfg(target_os = "macos")]
#[allow(non_snake_case, unused_variables, unused_results)]
fn show_alert_impl(title: &str, text: &str) {
    extern "C" {
        fn CFUserNotificationDisplayAlert(
            timeout: CFTimeInterval,
            flags: CFOptionFlags,
            iconURL: CFURLRef, soundURL: CFURLRef, localizationURL: CFURLRef,
            alertHeader: CFStringRef, alertMessage: CFStringRef,
            defaultButtonTitle: CFStringRef, alternateButtonTitle: CFStringRef, otherButtonTitle: CFStringRef,
            responseFlags: *mut CFOptionFlags,
        ) -> SInt32;
    }

    let header = CFString::new(title);
    let message = CFString::new(text);
    unsafe {
        CFUserNotificationDisplayAlert(
            0.0,
            0,  // kCFUserNotificationStopAlertLevel
            std::ptr::null(), std::ptr::null(), std::ptr::null(),
            header.as_concrete_TypeRef(), message.as_concrete_TypeRef(),
            std::ptr::null(), std::ptr::null(), std::ptr::null(),
            std::ptr::null_mut())
    };
}

#[cfg(not(any(target_os = "macos", target_os = "windows")))]
fn show_alert_impl(title: &str, text: &str) {
    let result = MessageDialog::new()
        .set_title(title)
        .set_text(text)
        .set_type(MessageType::Error)
        .show_alert();
    if let Err(e) = result {
        error!("Failed to show error message: {:?}", e);
        eprintln!("{}\n{}", title, text);
    }
}
