"""The downloads of the platform layout: the JBR of IDE Starter, the bundled runtime, the restart helper and the launcher.

`build/dependencies/dependencies.properties` pins every version.
"""

load(":download_helpers.bzl", "INTELLIJ_DEPENDENCIES_URL", "INTELLIJ_JBR_URL", "JBR_OS_SUFFIX", "maven_url", "pinned", "read_properties")
load(":download_rules.bzl", "download_repo", "extracted_download_repo")
load(":host_platforms.bzl", "HOST_PLATFORMS", "platform_parts")

_DEPENDENCIES = Label("//build:dependencies/dependencies.properties")

def _runtime_build_parts(runtime_build):
    major_version, separator, build_number = runtime_build.partition("b")
    if not separator or not major_version or not build_number:
        fail("'runtimeBuild' does not match '<update>b<build_number>': '%s'" % runtime_build)
    return struct(version = major_version, build_number = build_number)

def _jbr_archive_root(prefix, platform, runtime_build):
    parts = platform_parts(platform)
    build = _runtime_build_parts(runtime_build)
    return "%s%s-%s-%s-b%s" % (prefix, build.version, JBR_OS_SUFFIX[parts.os], parts.arch, build.build_number)

# JBRResolver.jbrArchiveFileName
def jbr_url(platform, runtime_build):
    return "%s/%s.tar.gz" % (INTELLIJ_JBR_URL, _jbr_archive_root("jbrsdk_jcef-", platform, runtime_build))

def jbr_runtime_archive_root(platform, runtime_build):
    """The one root directory of the [jbr_runtime_url] archive, such as `jbr-25.0.4.1-osx-aarch64-b635.70`."""
    return _jbr_archive_root("jbr-", platform, runtime_build)

# BundledRuntimeImpl.archiveName
def jbr_runtime_url(platform, runtime_build):
    """The vanilla runtime that a production distribution bundles as `jbr`, for one [HOST_PLATFORMS] entry."""
    return "%s/%s.tar.gz" % (INTELLIJ_JBR_URL, jbr_runtime_archive_root(platform, runtime_build))

def _platform_downloads_impl(module_ctx):
    dependencies = read_properties(module_ctx, _DEPENDENCIES)

    # NativeBinaryDownloader.getRestarter - one archive carrying `<os>-<arch>/restarter` for every platform,
    # so it is fetched once for all six. Every distribution's `bin` copies it, a dev one included.
    restarter_url = maven_url(
        INTELLIJ_DEPENDENCIES_URL,
        "org.jetbrains.intellij.deps",
        "restarter",
        pinned(dependencies, _DEPENDENCIES, "restarterBuild"),
        "tar.gz",
    )
    download_repo(
        name = "restarter",
        urls = [restarter_url],
    )

    # The same archive, unpacked. The platform layout declares `bin/restarter` out of it (`PlatformLayout.withRestarter`),
    # and the `platform_assets` component of a split distribution places the file from this repository.
    extracted_download_repo(
        name = "restarter_extracted",
        url = restarter_url,
    )

    # NativeBinaryDownloader.getLauncher - one archive with the native launcher of every platform, unpacked.
    # The plan states the launcher facts of each product. The `platform_assets` component of a split distribution
    # derives the files from them (`_launcher_files`) and places each one from this repository.
    extracted_download_repo(
        name = "launcher_extracted",
        url = maven_url(
            INTELLIJ_DEPENDENCIES_URL,
            "org.jetbrains.intellij.deps",
            "launcher",
            pinned(dependencies, _DEPENDENCIES, "launcherBuild"),
            "tar.gz",
        ),
    )

    runtime_build = pinned(dependencies, _DEPENDENCIES, "runtimeBuild")
    for platform in HOST_PLATFORMS:
        # The JBR that IDE Starter launches an IDE under test on
        download_repo(
            name = "jbr_%s" % platform,
            urls = [jbr_url(platform, runtime_build)],
        )

        # BundledRuntimeImpl.archiveName. The runtime that a production distribution bundles as `jbr`, unpacked.
        # A Windows target gives a file no executable bit, so only a macOS or Linux runtime records its list.
        extracted_download_repo(
            name = "jbr_runtime_%s_extracted" % platform,
            url = jbr_runtime_url(platform, runtime_build),
            strip_prefix = jbr_runtime_archive_root(platform, runtime_build),
            excludes = ["**/.DS_Store"],
            executable_list = platform_parts(platform).os != "windows",
        )

    # the same watched inputs always produce the same repositories, so this stays out of MODULE.bazel.lock
    return module_ctx.extension_metadata(reproducible = True)

platform_downloads = module_extension(implementation = _platform_downloads_impl)
