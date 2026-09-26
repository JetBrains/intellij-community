load("@rules_java//toolchains:remote_java_repository.bzl", "remote_java_repository")

JBR_VERSION = "25.0.2"
JBR_BUILD_VERSION = "b432.48"

def _remote_jbr25_repos():
    remote_java_repository(
        name = "remotejbr25_linux",
        prefix = "remotejbr",
        version = "25",
        target_compatible_with = [
            "@platforms//os:linux",
            "@platforms//cpu:x86_64",
        ],
        urls = ["https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-%s-linux-x64-%s.tar.gz" % (JBR_VERSION, JBR_BUILD_VERSION)],
        sha256 = "a76e8c1ef916f84d3b28e6794d1527d5189f9b0299a323e21ea737bdd93eaddd",
        strip_prefix = "jbrsdk-%s-linux-x64-%s" % (JBR_VERSION, JBR_BUILD_VERSION),
    )
    remote_java_repository(
        name = "remotejbr25_linux_aarch64",
        prefix = "remotejbr",
        version = "25",
        target_compatible_with = [
            "@platforms//os:linux",
            "@platforms//cpu:arm64",
        ],
        urls = ["https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-%s-linux-aarch64-%s.tar.gz" % (JBR_VERSION, JBR_BUILD_VERSION)],
        sha256 = "b2a7e10c80b9560bee42e2f6f69d4491dc74362a7ca378249ec545a83155eb57",
        strip_prefix = "jbrsdk-%s-linux-aarch64-%s" % (JBR_VERSION, JBR_BUILD_VERSION),
    )
    remote_java_repository(
        name = "remotejbr25_macos",
        prefix = "remotejbr",
        version = "25",
        target_compatible_with = [
            "@platforms//os:macos",
            "@platforms//cpu:x86_64",
        ],
        urls = ["https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-%s-osx-x64-%s.tar.gz" % (JBR_VERSION, JBR_BUILD_VERSION)],
        sha256 = "aeb433aef8bedcd8ba32963857923096a03bd87ca6ebb7a65a79d8b41b97dec3",
        strip_prefix = "jbrsdk-%s-osx-x64-%s/Contents/Home" % (JBR_VERSION, JBR_BUILD_VERSION),
    )
    remote_java_repository(
        name = "remotejbr25_macos_aarch64",
        prefix = "remotejbr",
        version = "25",
        target_compatible_with = [
            "@platforms//os:macos",
            "@platforms//cpu:arm64",
        ],
        urls = ["https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-%s-osx-aarch64-%s.tar.gz" % (JBR_VERSION, JBR_BUILD_VERSION)],
        sha256 = "1501ed8f15c1176abc895d6e3cf2b52562152f9731ec024b552e4bef24f02bf9",
        strip_prefix = "jbrsdk-%s-osx-aarch64-%s/Contents/Home" % (JBR_VERSION, JBR_BUILD_VERSION),
    )
    remote_java_repository(
        name = "remotejbr25_win",
        prefix = "remotejbr",
        version = "25",
        target_compatible_with = [
            "@platforms//os:windows",
            "@platforms//cpu:x86_64",
        ],
        urls = ["https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-%s-windows-x64-%s.tar.gz" % (JBR_VERSION, JBR_BUILD_VERSION)],
        sha256 = "48bf62ff4d61969066d71012e98ed6ef1292c4709badb099e22ed07195b00527",
        strip_prefix = "jbrsdk-%s-windows-x64-%s" % (JBR_VERSION, JBR_BUILD_VERSION),
    )
    remote_java_repository(
        name = "remotejbr25_win_arm64",
        prefix = "remotejbr",
        version = "25",
        target_compatible_with = [
            "@platforms//os:windows",
            "@platforms//cpu:arm64",
        ],
        urls = ["https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-%s-windows-aarch64-%s.tar.gz" % (JBR_VERSION, JBR_BUILD_VERSION)],
        sha256 = "2f3495e5e2411bb08c30d877ecc49f24af8393f83ec54c0ed730987761ffe2ec",
        strip_prefix = "jbrsdk-%s-windows-aarch64-%s" % (JBR_VERSION, JBR_BUILD_VERSION),
    )

def _jbr_toolchains_impl(ctx):
    _remote_jbr25_repos()

jbr_toolchains = module_extension(_jbr_toolchains_impl)
