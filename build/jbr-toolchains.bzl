load("@rules_java//toolchains:remote_java_repository.bzl", "remote_java_repository")

def _remote_jbr17_repos():
    remote_java_repository(
        name = "remotejbr17_linux",
        prefix = "remotejbr",
        version = "17",
        target_compatible_with = [
            "@platforms//os:linux",
            "@platforms//cpu:x86_64",
        ],
        urls = ["https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-17.0.15-linux-x64-b1381.5.tar.gz"],
        sha256 = "e8d3fdb6b1a4c3f6bd9c41d6349009d30f2447c5b19ebf8fa5e1331e85b0a8b9",
        strip_prefix = "jbrsdk-17.0.15-linux-x64-b1381.5",
    )
    remote_java_repository(
        name = "remotejbr17_linux_aarch64",
        prefix = "remotejbr",
        version = "17",
        target_compatible_with = [
            "@platforms//os:linux",
            "@platforms//cpu:arm64",
        ],
        urls = ["https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-17.0.15-linux-aarch64-b1381.5.tar.gz"],
        sha256 = "9014872291048d861063b2cc1042ff8d73bc16d0f0027d714e7877e1aee9aa28",
        strip_prefix = "jbrsdk-17.0.15-linux-aarch64-b1381.5",
    )
    remote_java_repository(
        name = "remotejbr17_macos",
        prefix = "remotejbr",
        version = "17",
        target_compatible_with = [
            "@platforms//os:macos",
            "@platforms//cpu:x86_64",
        ],
        urls = ["https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-17.0.15-osx-x64-b1381.5.tar.gz"],
        sha256 = "1318eaae68ec52845b48f8881a8e641dc90bd853f14c6eda4e0973ba54a7c806",
        strip_prefix = "jbrsdk-17.0.15-osx-x64-b1381.5/Contents/Home",
    )
    remote_java_repository(
        name = "remotejbr17_macos_aarch64",
        prefix = "remotejbr",
        version = "17",
        target_compatible_with = [
            "@platforms//os:macos",
            "@platforms//cpu:arm64",
        ],
        urls = ["https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-17.0.15-osx-aarch64-b1381.5.tar.gz"],
        sha256 = "d336f3813715d4c790379f0cf6db9f3f2e5518af724acbcf5199ceeb592dbe1f",
        strip_prefix = "jbrsdk-17.0.15-osx-aarch64-b1381.5/Contents/Home",
    )
    remote_java_repository(
        name = "remotejbr17_win",
        prefix = "remotejbr",
        version = "17",
        target_compatible_with = [
            "@platforms//os:windows",
            "@platforms//cpu:x86_64",
        ],
        urls = ["https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-17.0.15-windows-x64-b1381.5.tar.gz"],
        sha256 = "a263f435015cab58eed5f7906038e2e538c32a5283ff8acbdd10c6054f610147",
        strip_prefix = "jbrsdk-17.0.15-windows-x64-b1381.5",
    )
    remote_java_repository(
        name = "remotejbr17_win_arm64",
        prefix = "remotejbr",
        version = "17",
        target_compatible_with = [
            "@platforms//os:windows",
            "@platforms//cpu:arm64",
        ],
        urls = ["https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-17.0.15-windows-aarch64-b1381.5.tar.gz"],
        sha256 = "9e22e9e402c0d79fc09a18656660ed1ba84ce3d12eab28d29e5df625d890ca90",
        strip_prefix = "jbrsdk-17.0.15-windows-aarch64-b1381.5",
    )

def _jbr_toolchains_impl(ctx):
    _remote_jbr17_repos()

jbr_toolchains = module_extension(_jbr_toolchains_impl)
