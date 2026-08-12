"""Unit tests for the URL builders in dev_launch_dependencies.bzl.

What is at risk here is the *shape* of each URL, not the version in it: versions are read from the files
that pin them and so cannot drift, while a shape mirrors a build-side Kotlin function that this file
cannot call. The goldens below were checked against the servers that serve them, for every platform.

A wrong shape is otherwise found late - `bazel fetch` fails for that platform, or the launch reports an
undeclared URL - and only on the platform that has it wrong.
"""

load("@bazel_skylib//lib:unittest.bzl", "asserts", "unittest")
load(
    ":dev_launch_dependencies.bzl",
    "HOST_PLATFORMS",
    "jbr_url",
    "jcef_url",
    "lib_ghostty_vt_url",
    "maven_coordinates_urls",
    "maven_url",
    "platform_parts",
)

_JBR = "https://cache-redirector.jetbrains.com/intellij-jbr"
_MAVEN = "https://cache-redirector.jetbrains.com/repo.maven.apache.org/maven2"
_DEPS = "https://cache-redirector.jetbrains.com/intellij-dependencies"

# JBRResolver.jbrArchiveFileName, for `runtimeBuild=25.0.4b557.28`
_JBR_URLS = {
    "darwin_aarch64": _JBR + "/jbrsdk_jcef-25.0.4-osx-aarch64-b557.28.tar.gz",
    "darwin_x64": _JBR + "/jbrsdk_jcef-25.0.4-osx-x64-b557.28.tar.gz",
    "linux_aarch64": _JBR + "/jbrsdk_jcef-25.0.4-linux-aarch64-b557.28.tar.gz",
    "linux_x64": _JBR + "/jbrsdk_jcef-25.0.4-linux-x64-b557.28.tar.gz",
    "windows_aarch64": _JBR + "/jbrsdk_jcef-25.0.4-windows-aarch64-b557.28.tar.gz",
    "windows_x64": _JBR + "/jbrsdk_jcef-25.0.4-windows-x64-b557.28.tar.gz",
}

# CommunityRepositoryModules.jcefDownloadUrl, for `jcefBuild=263-b10`
_JCEF_URLS = {
    "darwin_aarch64": _JBR + "/jcef-osx-aarch64-263-b10.tar.gz",
    "darwin_x64": _JBR + "/jcef-osx-x64-263-b10.tar.gz",
    "linux_aarch64": _JBR + "/jcef-linux-aarch64-263-b10.tar.gz",
    "linux_x64": _JBR + "/jcef-linux-x64-263-b10.tar.gz",
    "windows_aarch64": _JBR + "/jcef-windows-aarch64-263-b10.tar.gz",
    "windows_x64": _JBR + "/jcef-windows-x64-263-b10.tar.gz",
}

def _every_platform_is_covered_test_impl(ctx):
    env = unittest.begin(ctx)
    asserts.equals(env, sorted(HOST_PLATFORMS), sorted(_JBR_URLS))
    asserts.equals(env, sorted(HOST_PLATFORMS), sorted(_JCEF_URLS))
    for platform in HOST_PLATFORMS:
        parts = platform_parts(platform)
        asserts.equals(env, platform, "%s_%s" % (parts.os, parts.arch))
    return unittest.end(env)

every_platform_is_covered_test = unittest.make(_every_platform_is_covered_test_impl)

def _jbr_url_test_impl(ctx):
    env = unittest.begin(ctx)
    for platform, expected in _JBR_URLS.items():
        asserts.equals(env, expected, jbr_url(platform, "25.0.4b557.28"), platform)
    return unittest.end(env)

jbr_url_test = unittest.make(_jbr_url_test_impl)

def _jcef_url_test_impl(ctx):
    env = unittest.begin(ctx)
    for platform, expected in _JCEF_URLS.items():
        asserts.equals(env, expected, jcef_url(platform, "263-b10"), platform)
    return unittest.end(env)

jcef_url_test = unittest.make(_jcef_url_test_impl)

def _lib_ghostty_vt_url_test_impl(ctx):
    env = unittest.begin(ctx)
    asserts.equals(
        env,
        "https://packages.jetbrains.team/files/p/ij/intellij-build-dependencies/libghostty-vt/abc123/libghostty-vt.zip.zst",
        lib_ghostty_vt_url("abc123"),
    )
    return unittest.end(env)

lib_ghostty_vt_url_test = unittest.make(_lib_ghostty_vt_url_test_impl)

# BuildDependenciesDownloader.getUriForMavenArtifact
def _maven_url_test_impl(ctx):
    env = unittest.begin(ctx)
    asserts.equals(
        env,
        _DEPS + "/org/jetbrains/intellij/deps/libwebp/1.6.0/libwebp-1.6.0.tar.gz",
        maven_url(_DEPS, "org.jetbrains.intellij.deps", "libwebp", "1.6.0", "tar.gz"),
    )
    asserts.equals(
        env,
        _MAVEN + "/org/apache/maven/apache-maven/3.9.16/apache-maven-3.9.16-bin.zip",
        maven_url(_MAVEN, "org.apache.maven", "apache-maven", "3.9.16", "zip", classifier = "bin"),
    )
    return unittest.end(env)

maven_url_test = unittest.make(_maven_url_test_impl)

def _maven_coordinates_urls_test_impl(ctx):
    env = unittest.begin(ctx)
    asserts.equals(
        env,
        [
            _MAVEN + "/org/apache/lucene/lucene-core/2.4.1/lucene-core-2.4.1.jar",
            _MAVEN + "/com/fasterxml/jackson/core/jackson-core/2.16.0/jackson-core-2.16.0.jar",
        ],
        maven_coordinates_urls(
            _MAVEN,
            "org.apache.lucene:lucene-core:2.4.1, com.fasterxml.jackson.core:jackson-core:2.16.0",
        ),
    )
    return unittest.end(env)

maven_coordinates_urls_test = unittest.make(_maven_coordinates_urls_test_impl)

def dev_launch_dependencies_test_suite(name):
    """Test suite for the URL builders in dev_launch_dependencies.bzl."""
    unittest.suite(
        name,
        every_platform_is_covered_test,
        jbr_url_test,
        jcef_url_test,
        lib_ghostty_vt_url_test,
        maven_url_test,
        maven_coordinates_urls_test,
    )
