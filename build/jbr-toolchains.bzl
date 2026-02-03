load("@rules_java//toolchains:remote_java_repository.bzl", "remote_java_repository")

def _remote_jbr21_repos():
    remote_java_repository(
        name = "remotejbr21_linux",
        prefix = "remotejbr",
        version = "21",
        target_compatible_with = [
            "@platforms//os:linux",
            "@platforms//cpu:x86_64",
        ],
        urls = ["https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-21.0.8-linux-x64-b1082.29.tar.gz"],
        sha256 = "dbdbe4714759144e6fdde42dc651456f3bbb0f2f5be98ec1b608c347a0ccc805",
        strip_prefix = "jbrsdk-21.0.8-linux-x64-b1082.29",
    )
    remote_java_repository(
        name = "remotejbr21_linux_aarch64",
        prefix = "remotejbr",
        version = "21",
        target_compatible_with = [
            "@platforms//os:linux",
            "@platforms//cpu:arm64",
        ],
        urls = ["https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-21.0.8-linux-aarch64-b1082.29.tar.gz"],
        sha256 = "5bdd2dcd7a92ccb6e21a46c306dcfcd647f8b55409f973a38c1b86c353655c25",
        strip_prefix = "jbrsdk-21.0.8-linux-aarch64-b1082.29",
    )
    remote_java_repository(
        name = "remotejbr21_macos",
        prefix = "remotejbr",
        version = "21",
        target_compatible_with = [
            "@platforms//os:macos",
            "@platforms//cpu:x86_64",
        ],
        urls = ["https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-21.0.8-osx-x64-b1082.29.tar.gz"],
        sha256 = "181b5505dffbb28a5ee072895d209f64186ad866aa69e30c0f501ae570c7cf1d",
        strip_prefix = "jbrsdk-21.0.8-osx-x64-b1082.29/Contents/Home",
    )
    remote_java_repository(
        name = "remotejbr21_macos_aarch64",
        prefix = "remotejbr",
        version = "21",
        target_compatible_with = [
            "@platforms//os:macos",
            "@platforms//cpu:arm64",
        ],
        urls = ["https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-21.0.8-osx-aarch64-b1082.29.tar.gz"],
        sha256 = "1414746883bd4baefccee22073ba1e4b78639169874288dde4e4cbe7f145cf2b",
        strip_prefix = "jbrsdk-21.0.8-osx-aarch64-b1082.29/Contents/Home",
    )
    remote_java_repository(
        name = "remotejbr21_win",
        prefix = "remotejbr",
        version = "21",
        target_compatible_with = [
            "@platforms//os:windows",
            "@platforms//cpu:x86_64",
        ],
        urls = ["https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-21.0.8-windows-x64-b1082.29.tar.gz"],
        sha256 = "967d757148a09bcb89b9fe858e2b167b28ea46ff7715e34164cec23a7736ff6b",
        strip_prefix = "jbrsdk-21.0.8-windows-x64-b1082.29",
    )
    remote_java_repository(
        name = "remotejbr21_win_arm64",
        prefix = "remotejbr",
        version = "21",
        target_compatible_with = [
            "@platforms//os:windows",
            "@platforms//cpu:arm64",
        ],
        urls = ["https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-21.0.8-windows-aarch64-b1082.29.tar.gz"],
        sha256 = "38619df25a4f6c790281214ac4e3b312b8c42a9bce1b57f4612cf8c5604654a5",
        strip_prefix = "jbrsdk-21.0.8-windows-aarch64-b1082.29",
    )

def _jbr_toolchains_impl(ctx):
    _remote_jbr21_repos()

jbr_toolchains = module_extension(_jbr_toolchains_impl)
