// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// this attribute doesn't do anything on macos/linux
// same as /SUBSYSTEM:WINDOWS, doesn't allocate/attach to a console by default
#![windows_subsystem = "windows"]
use xplat_launcher::main_lib;

fn main() {
    main_lib();
}
