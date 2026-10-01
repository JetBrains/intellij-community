// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The fixtures that the tests of the modules share. The tests of a module are in `<module>/tests.rs`. `testjar` writes
//! and reads the jars, and `golden` holds the frozen digests of the packed jars.

pub(crate) mod golden;
pub(crate) mod testjar;
