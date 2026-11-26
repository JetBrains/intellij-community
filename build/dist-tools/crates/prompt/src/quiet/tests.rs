// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use crate::quiet::quiet_settings;

#[test]
fn only_the_echo_and_the_signal_keys_go_off() {
    // SAFETY: `termios` is a C struct of integers and arrays, so all zero bytes are a valid value.
    let mut termios: libc::termios = unsafe { std::mem::zeroed() };
    termios.c_lflag = libc::ECHO | libc::ICANON | libc::ISIG | libc::ECHOE | libc::IEXTEN;
    termios.c_iflag = libc::ICRNL;
    termios.c_oflag = libc::OPOST;
    termios.c_cflag = libc::CS8;
    let quiet = quiet_settings(termios);
    assert_eq!(quiet.c_lflag, libc::ICANON | libc::ECHOE | libc::IEXTEN);
    assert_eq!(quiet.c_iflag, termios.c_iflag);
    assert_eq!(quiet.c_oflag, termios.c_oflag);
    assert_eq!(quiet.c_cflag, termios.c_cflag);
    assert_eq!(quiet.c_cc, termios.c_cc);
}
