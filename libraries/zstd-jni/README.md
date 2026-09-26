# zstd-jni

This module wraps `com.github.luben:zstd-jni`.

## Version pin

Keep the version at `1.5.7-16`. Do not update to `1.5.7-18` or later without the check below.

Since `1.5.7-18`, the jar is a multi-release jar. It adds Java 22 class files
(major version 66) under `META-INF/versions/22/com/github/luben/zstd/`:
`ZstdBinding*`, `ZstdInputStreamNoFinalizer` and `ZstdOutputStreamNoFinalizer`.
They implement the FFM-based binding.

`intellij.platform.buildScripts.downloader` uses `ZstdInputStreamNoFinalizer`.
The Android test framework uses the downloader to fetch the SDK.
The Gradle Tooling API walks the class graph of a `BuildAction` with `ClasspathInferer`.
On a JDK 22 or newer runtime, the class loader returns the Java 22 variant of the class.
The ASM version bundled in Gradle rejects it:

```
java.lang.IllegalArgumentException: Unsupported class file major version 66
    at org.objectweb.asm.ClassReader.<init>(ClassReader.java:199)
    at org.gradle.tooling.internal.provider.serialization.ClasspathInferer.find(...)
```

This fails `ApkAnalyzerGradleTokenIntegrationTest.testGetDefaultApkFile` for every AGP variant
in `ijplatform_master_Idea_Tests_AndroidPluginTests`.

## Before an update

Confirm one of these conditions:

- Every Gradle version in the Android test matrix bundles an ASM that accepts class file major version 66.
- The Android tests run on a JDK older than 22, so the class loader does not select the `versions/22` entry.
- The downloader no longer references `zstd-jni` classes that have a `versions/22` variant.

Then run the Android plugin tests on TeamCity and confirm `ApkAnalyzerGradleTokenIntegrationTest` is green.
