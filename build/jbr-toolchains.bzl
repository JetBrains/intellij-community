load("@rules_java//toolchains:remote_java_repository.bzl", "remote_java_repository")

JBR21_VERSION = "21.0.10"
JBR21_BUILD_VERSION = "1163.108"
_JBR21_ARCHIVE_BUILD_VERSION = "b" + JBR21_BUILD_VERSION

def _remote_jbr21_repos():
    remote_java_repository(
        name = "remotejbr21_linux",
        prefix = "remotejbr",
        version = "21",
        target_compatible_with = [
            "@platforms//os:linux",
            "@platforms//cpu:x86_64",
        ],
        urls = ["https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-%s-linux-x64-%s.tar.gz" % (JBR21_VERSION, _JBR21_ARCHIVE_BUILD_VERSION)],
        sha256 = "40d7c7e531a89394657cf66baa9b026acbe97f6e5f6750a6cc34f103a2e3bab4",
        strip_prefix = "jbrsdk-%s-linux-x64-%s" % (JBR21_VERSION, _JBR21_ARCHIVE_BUILD_VERSION),
    )
    remote_java_repository(
        name = "remotejbr21_linux_aarch64",
        prefix = "remotejbr",
        version = "21",
        target_compatible_with = [
            "@platforms//os:linux",
            "@platforms//cpu:arm64",
        ],
        urls = ["https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-%s-linux-aarch64-%s.tar.gz" % (JBR21_VERSION, _JBR21_ARCHIVE_BUILD_VERSION)],
        sha256 = "b20b1428d450de1a7e0be1103343568e3eb788d7790643eb255c4063ffce3ba2",
        strip_prefix = "jbrsdk-%s-linux-aarch64-%s" % (JBR21_VERSION, _JBR21_ARCHIVE_BUILD_VERSION),
    )
    remote_java_repository(
        name = "remotejbr21_macos",
        prefix = "remotejbr",
        version = "21",
        target_compatible_with = [
            "@platforms//os:macos",
            "@platforms//cpu:x86_64",
        ],
        urls = ["https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-%s-osx-x64-%s.tar.gz" % (JBR21_VERSION, _JBR21_ARCHIVE_BUILD_VERSION)],
        sha256 = "3da3e56ec02b5c0f6e626e03ed6d33ea7238368040383544a41384a195af1eac",
        strip_prefix = "jbrsdk-%s-osx-x64-%s/Contents/Home" % (JBR21_VERSION, _JBR21_ARCHIVE_BUILD_VERSION),
    )
    remote_java_repository(
        name = "remotejbr21_macos_aarch64",
        prefix = "remotejbr",
        version = "21",
        target_compatible_with = [
            "@platforms//os:macos",
            "@platforms//cpu:arm64",
        ],
        urls = ["https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-%s-osx-aarch64-%s.tar.gz" % (JBR21_VERSION, _JBR21_ARCHIVE_BUILD_VERSION)],
        sha256 = "298fe55cceff7dad3b11636490e67ce531840ffa4121fe8f5f3813d56c5f4edc",
        strip_prefix = "jbrsdk-%s-osx-aarch64-%s/Contents/Home" % (JBR21_VERSION, _JBR21_ARCHIVE_BUILD_VERSION),
    )
    remote_java_repository(
        name = "remotejbr21_win",
        prefix = "remotejbr",
        version = "21",
        target_compatible_with = [
            "@platforms//os:windows",
            "@platforms//cpu:x86_64",
        ],
        urls = ["https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-%s-windows-x64-%s.tar.gz" % (JBR21_VERSION, _JBR21_ARCHIVE_BUILD_VERSION)],
        sha256 = "22447ab931bff1519b5b89506a6631bb34d41f9df51e1e004394d9505510d9da",
        strip_prefix = "jbrsdk-%s-windows-x64-%s" % (JBR21_VERSION, _JBR21_ARCHIVE_BUILD_VERSION),
    )
    remote_java_repository(
        name = "remotejbr21_win_arm64",
        prefix = "remotejbr",
        version = "21",
        target_compatible_with = [
            "@platforms//os:windows",
            "@platforms//cpu:arm64",
        ],
        urls = ["https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-%s-windows-aarch64-%s.tar.gz" % (JBR21_VERSION, _JBR21_ARCHIVE_BUILD_VERSION)],
        sha256 = "b1362e2148850b051bf247d729d96f0fcb86d2dc93324e356d466c0b8249675d",
        strip_prefix = "jbrsdk-%s-windows-aarch64-%s" % (JBR21_VERSION, _JBR21_ARCHIVE_BUILD_VERSION),
    )

def _jbr_toolchains_impl(ctx):
    _remote_jbr21_repos()

jbr_toolchains = module_extension(_jbr_toolchains_impl)
