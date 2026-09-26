// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// additional console entry point for Windows (the main one is a GUI app)
#![cfg(target_os = "windows")]
use xplat_launcher::main_lib;

fn main() {
    main_lib();
}
