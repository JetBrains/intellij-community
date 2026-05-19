load("@rules_java//toolchains:remote_java_repository.bzl", "remote_java_repository")

def _remote_jbr25_repos():
    remote_java_repository(
        name = "remotejbr25_linux",
        prefix = "remotejbr",
        version = "25",
        target_compatible_with = [
            "@platforms//os:linux",
            "@platforms//cpu:x86_64",
        ],
        urls = ["https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-25.0.2-linux-x64-b315.62.tar.gz"],
        sha256 = "7e1614dce41044cd4777a51b2ed3224727b34bda8e569c1e2f687aebb0d8acb8",
        strip_prefix = "jbrsdk-25.0.2-linux-x64-b315.62",
    )
    remote_java_repository(
        name = "remotejbr25_linux_aarch64",
        prefix = "remotejbr",
        version = "25",
        target_compatible_with = [
            "@platforms//os:linux",
            "@platforms//cpu:arm64",
        ],
        urls = ["https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-25.0.2-linux-aarch64-b315.62.tar.gz"],
        sha256 = "ac94a6a0c80f0d4523a8c70363c5a93036406eb882ee6a393a7fb8b838213348",
        strip_prefix = "jbrsdk-25.0.2-linux-aarch64-b315.62",
    )
    remote_java_repository(
        name = "remotejbr25_macos",
        prefix = "remotejbr",
        version = "25",
        target_compatible_with = [
            "@platforms//os:macos",
            "@platforms//cpu:x86_64",
        ],
        urls = ["https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-25.0.2-osx-x64-b315.62.tar.gz"],
        sha256 = "8a8edb1b61b29d2ede8884c60ee52cd5246690b820f1bd171ab1707f6a00fb67",
        strip_prefix = "jbrsdk-25.0.2-osx-x64-b315.62/Contents/Home",
    )
    remote_java_repository(
        name = "remotejbr25_macos_aarch64",
        prefix = "remotejbr",
        version = "25",
        target_compatible_with = [
            "@platforms//os:macos",
            "@platforms//cpu:arm64",
        ],
        urls = ["https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-25.0.2-osx-aarch64-b315.62.tar.gz"],
        sha256 = "e7b135873d05d92a2886270eccf2ff2a46ee6d3558715f7e068dc70e044d911a",
        strip_prefix = "jbrsdk-25.0.2-osx-aarch64-b315.62/Contents/Home",
    )
    remote_java_repository(
        name = "remotejbr25_win",
        prefix = "remotejbr",
        version = "25",
        target_compatible_with = [
            "@platforms//os:windows",
            "@platforms//cpu:x86_64",
        ],
        urls = ["https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-25.0.2-windows-x64-b315.62.tar.gz"],
        sha256 = "8a7199d60d8d81e329a215b0790641eb4329be5f8c4c90e2f13654df15b0dd75",
        strip_prefix = "jbrsdk-25.0.2-windows-x64-b315.62",
    )
    remote_java_repository(
        name = "remotejbr25_win_arm64",
        prefix = "remotejbr",
        version = "25",
        target_compatible_with = [
            "@platforms//os:windows",
            "@platforms//cpu:arm64",
        ],
        urls = ["https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-25.0.2-windows-aarch64-b315.62.tar.gz"],
        sha256 = "8f00d4b4ab7c9ca38615620e876ad3343169caa7364ba30bf01e2f80797b738b",
        strip_prefix = "jbrsdk-25.0.2-windows-aarch64-b315.62",
    )

def _jbr_toolchains_impl(ctx):
    _remote_jbr25_repos()

jbr_toolchains = module_extension(_jbr_toolchains_impl)
