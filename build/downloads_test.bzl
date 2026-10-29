"""Unit tests for the download rules, the download helpers and the URL builders of the platform downloads.

What is at risk here is the *shape* of each URL, not the version in it: versions are read from the files
that pin them and so cannot drift, while a shape mirrors a build-side Kotlin function that this file
cannot call. The goldens below were checked against the servers that serve them, for every platform.

A wrong shape is otherwise found late - `bazel fetch` fails for that platform, or the launch reports an
undeclared URL - and only on the platform that has it wrong.
"""

load("@bazel_skylib//lib:unittest.bzl", "asserts", "unittest")
load(":download_helpers.bzl", "maven_coordinates_urls", "maven_url")
load(":download_rules.bzl", "all_downloads_pinned", "find_download_conflict", "manifest_content", "text_repo_files")
load(":host_platforms.bzl", "HOST_PLATFORMS", "platform_parts")
load(":platform_downloads.bzl", "jbr_runtime_archive_root", "jbr_runtime_url", "jbr_url")

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

# BundledRuntimeImpl.archiveName, for `runtimeBuild=25.0.4.1b635.70`
_JBR_RUNTIME_URLS = {
    "darwin_aarch64": _JBR + "/jbr-25.0.4.1-osx-aarch64-b635.70.tar.gz",
    "darwin_x64": _JBR + "/jbr-25.0.4.1-osx-x64-b635.70.tar.gz",
    "linux_aarch64": _JBR + "/jbr-25.0.4.1-linux-aarch64-b635.70.tar.gz",
    "linux_x64": _JBR + "/jbr-25.0.4.1-linux-x64-b635.70.tar.gz",
    "windows_aarch64": _JBR + "/jbr-25.0.4.1-windows-aarch64-b635.70.tar.gz",
    "windows_x64": _JBR + "/jbr-25.0.4.1-windows-x64-b635.70.tar.gz",
}

def _every_platform_is_covered_test_impl(ctx):
    env = unittest.begin(ctx)
    asserts.equals(env, sorted(HOST_PLATFORMS), sorted(_JBR_URLS))
    asserts.equals(env, sorted(HOST_PLATFORMS), sorted(_JBR_RUNTIME_URLS))
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

# The archive has one root, the file name without `.tar.gz`, and the extracted repository strips it.
def _jbr_runtime_url_test_impl(ctx):
    env = unittest.begin(ctx)
    for platform, expected in _JBR_RUNTIME_URLS.items():
        asserts.equals(env, expected, jbr_runtime_url(platform, "25.0.4.1b635.70"), platform)
        asserts.equals(
            env,
            expected.rpartition("/")[2].removesuffix(".tar.gz"),
            jbr_runtime_archive_root(platform, "25.0.4.1b635.70"),
            platform,
        )
    return unittest.end(env)

jbr_runtime_url_test = unittest.make(_jbr_runtime_url_test_impl)

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

    # NativeBinaryDownloader.getRestarter, for `restarterBuild=243.6005`. Worth pinning even though it is the
    # plain Maven shape: `PreloadedDownloads.findByUrl` matches the URL by exact string equality, so a shape
    # that drifts from `getUriForMavenArtifact` does not fall back to a download - it fails the assembly.
    asserts.equals(
        env,
        _DEPS + "/org/jetbrains/intellij/deps/restarter/243.6005/restarter-243.6005.tar.gz",
        maven_url(_DEPS, "org.jetbrains.intellij.deps", "restarter", "243.6005", "tar.gz"),
    )

    # NativeBinaryDownloader.getLauncher, for `launcherBuild=263.6784`. The same Maven shape as the restarter.
    asserts.equals(
        env,
        _DEPS + "/org/jetbrains/intellij/deps/launcher/263.6784/launcher-263.6784.tar.gz",
        maven_url(_DEPS, "org.jetbrains.intellij.deps", "launcher", "263.6784", "tar.gz"),
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

def _download_repository_integrity_test_impl(ctx):
    env = unittest.begin(ctx)
    pinned = struct(name = "one.zip", url = "https://example.test/one.zip", sha256 = "a" * 64)
    unpinned = struct(name = "two.zip", url = "https://example.test/two.zip", sha256 = "")

    asserts.true(env, all_downloads_pinned([pinned]))
    asserts.false(env, all_downloads_pinned([pinned, unpinned]))
    asserts.equals(env, None, find_download_conflict([pinned, unpinned]))
    asserts.equals(
        env,
        "download repository has duplicate output file name: one.zip",
        find_download_conflict([pinned, struct(name = pinned.name, url = unpinned.url, sha256 = "")]),
    )
    asserts.equals(
        env,
        "download repository has duplicate URL: https://example.test/one.zip",
        find_download_conflict([pinned, struct(name = unpinned.name, url = pinned.url, sha256 = "")]),
    )
    return unittest.end(env)

download_repository_integrity_test = unittest.make(_download_repository_integrity_test_impl)

def _manifest_content_test_impl(ctx):
    env = unittest.begin(ctx)

    # A repository that declares no file still writes a manifest, and `PreloadedDownloads.parse` rejects a
    # blank row, so the header must stand alone rather than gain an empty line after it.
    asserts.equals(env, "intellij-build-downloads\t1\n", manifest_content([]))
    asserts.equals(
        env,
        "intellij-build-downloads\t1\none.zip\t%s\thttps://example.test/one.zip\n" % ("a" * 64),
        manifest_content(["one.zip\t%s\thttps://example.test/one.zip" % ("a" * 64)]),
    )
    asserts.equals(
        env,
        "intellij-build-downloads\t1\nfirst\nsecond\n",
        manifest_content(["first", "second"]),
    )
    return unittest.end(env)

manifest_content_test = unittest.make(_manifest_content_test_impl)

# The file holds the value byte for byte, with no trailing newline, as `Files.writeString` of the production generator.
def _text_repo_files_test_impl(ctx):
    env = unittest.begin(ctx)
    asserts.equals(
        env,
        {
            "BUILD": 'package(default_visibility = ["//visibility:public"])\n\nexports_files(["build.txt"])\n',
            "build.txt": "3.2.0.65851",
        },
        text_repo_files("build.txt", "3.2.0.65851"),
    )
    return unittest.end(env)

text_repo_files_test = unittest.make(_text_repo_files_test_impl)

def downloads_test_suite(name):
    """Test suite for the download rules, the download helpers and the platform downloads."""
    unittest.suite(
        name,
        every_platform_is_covered_test,
        jbr_url_test,
        jbr_runtime_url_test,
        maven_url_test,
        maven_coordinates_urls_test,
        manifest_content_test,
        download_repository_integrity_test,
        text_repo_files_test,
    )
