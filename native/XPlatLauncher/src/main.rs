// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// main entry point (the attribute is ignored on Unix targets)
#![windows_subsystem = "windows"]
use xplat_launcher::main_lib;

fn main() {
    main_lib();
}
