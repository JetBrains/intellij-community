// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
use std::ffi::NulError;
use std::fmt::{Display, Formatter};
use log::error;
use thiserror::Error;

pub type Result<T> = std::result::Result<T, LauncherError>;

#[derive(Error, Debug)]
pub enum LauncherError {
    #[error("Error {0}")]
    HumanReadableError(#[from] HumanReadableError),
    #[error("I/O error {0}")]
    IoError(#[from] std::io::Error),
    #[error("Var error {0}")]
    VarError(#[from] std::env::VarError),
    #[error("JSON error {0}")]
    SerdeError(#[from] serde_json::Error),
    #[error("Libloading error {0}")]
    LibloadingError(#[from] libloading::Error),
    #[error("JNI error {0}")]
    JniError(#[from] JniError),
    #[error("Nul error {0}")]
    NulError(#[from] NulError),
    #[error("jni-rs error {0}")]
    JniRsError(#[from] jni::errors::Error),
    #[error("SetLoggerError error {0}")]
    LogSetLoggerError(#[from] log::SetLoggerError),    
    
}

pub fn err_from_string<T,S: AsRef<str>>(message: S) -> Result<T> {
    let error = HumanReadableError { message: message.as_ref().to_string() };
    return Err(LauncherError::HumanReadableError(error));
}

#[derive(Error, Debug)]
pub struct HumanReadableError {
    pub message: String,
}

#[derive(Error, Debug)]
pub struct JniError {
    pub message: String
}

impl JniError {
    fn err_from_string(message: &str) -> Result<()> {
        let error = JniError { message: message.to_string() };
        return Err(LauncherError::JniError(error));
    }

    pub fn check_result(error_code: jni_sys::jint) -> Result<()> {
        match error_code {
            jni_sys::JNI_OK => Ok(()),
            jni_sys::JNI_ERR => JniError::err_from_string("JNI_ERR: unknown error"),
            jni_sys::JNI_EDETACHED => JniError::err_from_string("JNI_EDETACHED: thread is not attached to JVM"),
            jni_sys::JNI_EVERSION => JniError::err_from_string("JNI_EVERSION: wrong JNI version"),
            jni_sys::JNI_ENOMEM => JniError::err_from_string("JNI_ENOMEM: no enought memory"),
            jni_sys::JNI_EEXIST => JniError::err_from_string("JNI_EEXIST: JVM already exists"),
            jni_sys::JNI_EINVAL => JniError::err_from_string("JNI_EINVAL? invalid arguments"),
            i => JniError::err_from_string(format!("Other: {i}").as_str()),
        }
    }
}

impl Display for JniError {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}", self.message)
    }
}

impl Display for HumanReadableError {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}", self.message)
    }
}