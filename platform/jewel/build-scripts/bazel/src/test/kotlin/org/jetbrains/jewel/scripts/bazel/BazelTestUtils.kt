package org.jetbrains.jewel.scripts.bazel

import java.io.File

fun createSafeTempDir(subDirectory: String) = File(System.getenv("TEST_TMPDIR") ?: System.getProperty("java.io.tmpdir"))
    .resolve(subDirectory).apply { mkdirs() }
