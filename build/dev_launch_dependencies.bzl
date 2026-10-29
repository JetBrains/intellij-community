"""The archives a dev-mode IDE assembly downloads at launch time, as Bazel inputs.

The versions are not declared here. They are read out of the files that already own them, so a
dependency updater bumping a version cannot leave this set behind. `ijentVersion` alone moved 531 times
in six months; nothing hand-maintained can track that.

The URL shapes, unlike the versions, are stable. Each one mirrors the build-side function named beside
it, and a divergence surfaces at launch as an undeclared URL.

This is a module extension rather than a plain repository rule because watching is per file: one
repository reading `build/dependencies/dependencies.properties` would be invalidated by every commit to
it, and refetching means re-downloading hundreds of megabytes. The extension reads the coarse files and
hands each group of artifacts a repository whose attributes carry only its own version, so a bump
refetches exactly what it changed.

A repository is per platform only where the artifact is: the JBR, JCEF, pyrefly, the Chatter binary and the
Toolbox daemon are, while IJent, libwebp, libghostty-vt, the restart helper and the bundled Maven
distribution ship every platform in one archive and are fetched once for all six.

This file owns the community half: the groups whose version lives under `community/`. The repository rules are in
`download_rules.bzl`, and the helpers of an extension are in `download_helpers.bzl`. The ultimate half is
`//build:dev_launch_dependencies.bzl`, and the two compose through [merge_repo_sets].
"""

load(
    ":download_helpers.bzl",
    "INTELLIJ_BUILD_DEPENDENCIES_URL",
    "INTELLIJ_DEPENDENCIES_URL",
    "INTELLIJ_JBR_URL",
    "MAVEN_CENTRAL_URL",
    "maven_coordinates_urls",
    "maven_url",
    "pinned",
    "read_properties",
)
load(":download_rules.bzl", "EXECUTABLE_LIST_NAME", "MANIFEST_NAME", "download_repo", "extracted_download_repo", "text_repo")
load(":host_platforms.bzl", "HOST_PLATFORMS", "platform_parts")

# OsFamily.jbrArchiveSuffix
JBR_OS_SUFFIX = {
    "darwin": "osx",
    "linux": "linux",
    "windows": "windows",
}

_COMMUNITY_DEPENDENCIES = Label("//build:dependencies/dependencies.properties")

# BuildDependenciesConstants.PRELOADED_DOWNLOADS_MANIFEST_PROPERTY
_MANIFEST_PROPERTY = "intellij.build.download.preloaded.manifest"

# BuildDependenciesConstants.PRELOADED_DOWNLOADS_ONLY_PROPERTY
_ONLY_PROPERTY = "intellij.build.download.preloaded.only"

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

# CommunityRepositoryModules.jcefDownloadUrl
def jcef_url(platform, jcef_build):
    parts = platform_parts(platform)
    return "%s/jcef-%s-%s-%s.tar.gz" % (INTELLIJ_JBR_URL, JBR_OS_SUFFIX[parts.os], parts.arch, jcef_build)

# TerminalLibGhosttyVtDownloader.downloadUrl
def lib_ghostty_vt_url(version):
    return "%s/libghostty-vt/libghostty-vt-%s.zip.zst" % (INTELLIJ_BUILD_DEPENDENCIES_URL, version)

# CommunityRepositoryModules.jSerialCommDownloadUrl - the content hash is the version. Keep in lockstep with
# `JSERIALCOMM_NATIVE_HASH` in CommunityRepositoryModules.kt.
_JSERIALCOMM_NATIVE_HASH = "9a7813435b79aa2e23c7f2a78f1b66b48c0504c4"

def jserialcomm_url():
    return "%s/jSerialComm/%s/jSerialComm.zip" % (INTELLIJ_BUILD_DEPENDENCIES_URL, _JSERIALCOMM_NATIVE_HASH)

# OsFamily.osName
_PYREFLY_OS_NAMES = {
    "darwin": "macOS",
    "linux": "Linux",
    "windows": "Windows",
}

# JvmArchitecture.archName
_PYREFLY_ARCH_NAMES = {
    "aarch64": "AArch64",
    "x64": "X86_64",
}

def pyrefly_platform_directory(platform):
    """`pyreflyPlatformDirName`: the directory of the binary in the archive and in the plugin, such as `macOS-AArch64`."""
    parts = platform_parts(platform)
    return "%s-%s" % (_PYREFLY_OS_NAMES[parts.os], _PYREFLY_ARCH_NAMES[parts.arch])

# downloadPyrefly - `platform` is a [HOST_PLATFORMS] entry, or `None` for the license report archive
def pyrefly_url(platform, version):
    artifact = "pyrefly-" + (pyrefly_platform_directory(platform).lower() if platform else "license")
    return maven_url(INTELLIJ_DEPENDENCIES_URL, "org.jetbrains.intellij.deps", artifact, version, "tar.gz")

def merge_repo_sets(*repo_sets):
    """Unions repository sets platform by platform, preserving order and dropping repeats."""
    merged = {}
    for repo_set in repo_sets:
        for platform, entries in repo_set.items():
            existing = merged.setdefault(platform, [])
            for entry in entries:
                if entry not in existing:
                    existing.append(entry)
    return merged

def repo_entries(make_label, repositories):
    """The `files` and the manifest label of each repository in [repositories], for a repository set.

    [make_label] is `Label` of the calling `.bzl` file. `Label` reads the repo mapping of the file that calls it, so
    each half resolves the names of the repositories that its own module imports.
    """
    return [
        struct(files = make_label("@%s//:files" % repository), manifest = make_label("@%s//:%s" % (repository, MANIFEST_NAME)))
        for repository in repositories
    ]

def shared_repo_set(make_label, repositories):
    """A repository set that holds [repositories] on every [HOST_PLATFORMS] entry."""
    return {platform: repo_entries(make_label, repositories) for platform in HOST_PLATFORMS}

def per_platform_repo_set(make_label, repository_of):
    """A repository set that holds the repository `repository_of(platform)` on each [HOST_PLATFORMS] entry."""
    return {platform: repo_entries(make_label, [repository_of(platform)]) for platform in HOST_PLATFORMS}

def _label(value):
    return Label(value)

def _restricted(repo_set, platforms):
    if platforms == None:
        return repo_set
    for platform in platforms:
        if platform not in repo_set:
            fail("'%s' is not one of the platforms this set covers: %s" % (platform, sorted(repo_set)))
    return {platform: repo_set[platform] for platform in platforms}

def _file_labels(entries):
    return [entry.files for entry in entries]

def _manifest_labels(entries):
    return [entry.manifest for entry in entries]

def _manifest_flag(entries):
    return ["-D%s=%s" % (_MANIFEST_PROPERTY, ",".join([
        "$(rlocationpath %s)" % entry.manifest
        for entry in entries
    ]))]

def _select_by_platform(repo_set, platforms, value):
    # A platform with nothing to preload falls through to the default rather than getting an empty flag,
    # which the runtime rejects: `-D<manifest>=` names no manifest, and saying nothing is what is meant.
    branches = {
        Label("//build:host_" + platform): value(entries)
        for platform, entries in _restricted(repo_set, platforms).items()
        if entries
    }
    branches["//conditions:default"] = []
    return select(branches)

def preloaded_downloads_data(repo_set, platforms = None):
    """The fetched archives, as runfiles of the consuming target."""
    return _select_by_platform(repo_set, platforms, _file_labels)

def preloaded_downloads_manifest_data(repo_set, platforms = None):
    """The manifests that go with [preloaded_downloads_data].

    Named one by one rather than reached through a filegroup, because `$(rlocationpath ...)` resolves a
    single file of a directly-named dependency.
    """
    return _select_by_platform(repo_set, platforms, _manifest_labels)

def preloaded_downloads_for_platform(repo_set, platform):
    """[preloaded_downloads_data] for one named platform, without a `select`.

    The `select`ing forms key on `//build:host_<platform>`, which answers "what is this build running
    on". A cross-assembled distribution needs the other question — what is it being built *for* — and
    those two differ by construction: a Linux distribution assembled on a macOS host wants the
    linux_aarch64 JBR, and a host select would hand it the darwin one.
    """
    return _file_labels(_restricted(repo_set, [platform])[platform])

def preloaded_manifests_for_platform(repo_set, platform):
    """The manifests that go with [preloaded_downloads_for_platform]."""
    return _manifest_labels(_restricted(repo_set, [platform])[platform])

def preloaded_downloads_flag(repo_set, platforms = None):
    """`-Dintellij.build.download.preloaded.manifest` for [preloaded_downloads_manifest_data].

    Comma-joined, never space-joined: the whole thing has to survive as one token in `jvm_flags`.
    """
    return _select_by_platform(repo_set, platforms, _manifest_flag)

def preloaded_downloads_only_flag(platforms):
    """Makes the configured manifests the complete inventory, so an undeclared URL fails.

    For a target that must not reach the network - a sandboxed test, a worker on a read-only share of the
    checkout - or one whose whole download set has been measured.

    [platforms] is where that holds, and it is mandatory because the answer is per platform: a set
    measured by assembling on macOS says nothing about the archives a Windows assembly reaches for. Off
    those platforms the target keeps advisory manifests rather than failing on the first one.
    """
    return _only_select(platforms, ["-D%s=true" % _ONLY_PROPERTY], [])

def _only_select(platforms, value, default):
    for platform in platforms:
        if platform not in HOST_PLATFORMS:
            fail("'%s' is not one of %s" % (platform, HOST_PLATFORMS))
    branches = {Label("//build:host_" + platform): value for platform in platforms}
    branches["//conditions:default"] = default
    return select(branches)

# What a dev-mode assembly downloads, of the groups community owns.
COMMUNITY_DEV_LAUNCH_REPOS = merge_repo_sets(
    shared_repo_set(_label, [
        "dev_launch_libghostty",
        "dev_launch_libwebp",
        "dev_launch_maven",
        "dev_launch_maven3_libraries",
        "dev_launch_maven_telemetry_libraries",
        "dev_launch_restarter",
        "dev_launch_jserialcomm",
    ]),
    per_platform_repo_set(_label, lambda platform: "dev_launch_%s_jcef" % platform),
)

# Just the bundled Maven distribution and its libraries: what a test that opens a Maven project needs,
# because `MavenDistributionsCache` resolves the bundled Maven through `BundledMavenDownloader` and would
# otherwise download it. Deliberately not the whole dev-launch set - a test has no use for JCEF.
MAVEN_DEV_LAUNCH_REPOS = shared_repo_set(_label, ["dev_launch_maven", "dev_launch_maven3_libraries", "dev_launch_maven_telemetry_libraries"])

# What IDE Starter downloads to launch an IDE under test. Deliberately apart from the dev-launch set: a
# `bazel run` dev launch runs on the JVM Bazel gave it and never touches these 400 MB.
COMMUNITY_IDE_STARTER_REPOS = per_platform_repo_set(_label, lambda platform: "dev_launch_%s_jbr" % platform)

def jbr_runtime_repository_label(platform):
    """The `files` filegroup of the unpacked production runtime of one [HOST_PLATFORMS] entry."""
    platform_parts(platform)
    return Label("@dev_launch_%s_jbr_runtime_extracted//:files" % platform)

def jbr_runtime_executable_list_label(platform):
    """The executable list of the repository of [jbr_runtime_repository_label], or `None` for a Windows platform.

    A Windows target gives a file no executable bit, so the collector does not read the list of a Windows runtime.
    """
    if platform_parts(platform).os == "windows":
        return None
    return Label("@dev_launch_%s_jbr_runtime_extracted//:%s" % (platform, EXECUTABLE_LIST_NAME))

def _dev_launch_deps_community_impl(module_ctx):
    community = read_properties(module_ctx, _COMMUNITY_DEPENDENCIES)

    download_repo(
        name = "dev_launch_libghostty",
        urls = [lib_ghostty_vt_url(pinned(community, _COMMUNITY_DEPENDENCIES, "libGhosttyVtVersion"))],
    )

    # CommunityRepositoryModules serial-monitor native library. One zip for every platform.
    download_repo(
        name = "dev_launch_jserialcomm",
        urls = [jserialcomm_url()],
    )
    download_repo(
        name = "dev_launch_libwebp",
        urls = [maven_url(
            INTELLIJ_DEPENDENCIES_URL,
            "org.jetbrains.intellij.deps",
            "libwebp",
            pinned(community, _COMMUNITY_DEPENDENCIES, "libwebpVersion"),
            "tar.gz",
        )],
    )

    # NativeBinaryDownloader.getRestarter - one archive carrying `<os>-<arch>/restarter` for every platform,
    # so it is fetched once for all six. Every distribution's `bin` copies it, a dev one included.
    restarter_url = maven_url(
        INTELLIJ_DEPENDENCIES_URL,
        "org.jetbrains.intellij.deps",
        "restarter",
        pinned(community, _COMMUNITY_DEPENDENCIES, "restarterBuild"),
        "tar.gz",
    )
    download_repo(
        name = "dev_launch_restarter",
        urls = [restarter_url],
    )

    # The same archive, unpacked. The platform layout declares `bin/restarter` out of it (`PlatformLayout.withRestarter`),
    # and the `platform_assets` component of a split distribution places the file from this repository.
    extracted_download_repo(
        name = "dev_launch_restarter_extracted",
        url = restarter_url,
    )

    # NativeBinaryDownloader.getLauncher - one archive with the native launcher of every platform, unpacked.
    # The plan states the launcher facts of each product. The `platform_assets` component of a split distribution
    # derives the files from them (`_launcher_files`) and places each one from this repository.
    extracted_download_repo(
        name = "dev_launch_launcher_extracted",
        url = maven_url(
            INTELLIJ_DEPENDENCIES_URL,
            "org.jetbrains.intellij.deps",
            "launcher",
            pinned(community, _COMMUNITY_DEPENDENCIES, "launcherBuild"),
            "tar.gz",
        ),
    )

    # BundledMavenDownloader.downloadMavenDistribution - the one archive of `:files`, so a plugin layout names it
    # without its version
    download_repo(
        name = "dev_launch_maven",
        urls = [maven_url(
            MAVEN_CENTRAL_URL,
            "org.apache.maven",
            "apache-maven",
            pinned(community, _COMMUNITY_DEPENDENCIES, "bundledMavenVersion"),
            "zip",
            classifier = "bin",
        )],
    )

    # BundledMavenDownloader.resolveMaven3Libs - a plugin layout copies the whole tree, so it names no jar
    download_repo(
        name = "dev_launch_maven3_libraries",
        urls = maven_coordinates_urls(
            MAVEN_CENTRAL_URL,
            pinned(community, _COMMUNITY_DEPENDENCIES, "bundledMaven3Libraries"),
        ),
    )

    # BundledMavenDownloader.resolveMavenTelemetryDependencies - a plugin layout copies the whole tree
    download_repo(
        name = "dev_launch_maven_telemetry_libraries",
        urls = maven_coordinates_urls(
            MAVEN_CENTRAL_URL,
            pinned(community, _COMMUNITY_DEPENDENCIES, "bundledMavenTelemetryLibraries"),
        ),
    )

    # downloadPyrefly - one archive per platform holds `<os>-<arch>/pyrefly`, and one archive holds the `license` tree.
    # The Python community plugin layout copies files of the unpacked archives, so each archive is an extracted
    # repository alone.
    pyrefly_build = pinned(community, _COMMUNITY_DEPENDENCIES, "pyreflyBuild")
    extracted_download_repo(
        name = "dev_launch_pyrefly_license_extracted",
        url = pyrefly_url(None, pyrefly_build),
    )
    for platform in HOST_PLATFORMS:
        extracted_download_repo(
            name = "dev_launch_%s_pyrefly_extracted" % platform,
            url = pyrefly_url(platform, pyrefly_build),
        )

    jcef_build = pinned(community, _COMMUNITY_DEPENDENCIES, "jcefBuild")
    runtime_build = pinned(community, _COMMUNITY_DEPENDENCIES, "runtimeBuild")
    for platform in HOST_PLATFORMS:
        download_repo(
            name = "dev_launch_%s_jcef" % platform,
            urls = [jcef_url(platform, jcef_build)],
        )
        download_repo(
            name = "dev_launch_%s_jbr" % platform,
            urls = [jbr_url(platform, runtime_build)],
        )

        # BundledRuntimeImpl.archiveName. The runtime that a production distribution bundles as `jbr`, unpacked.
        extracted_download_repo(
            name = "dev_launch_%s_jbr_runtime_extracted" % platform,
            url = jbr_runtime_url(platform, runtime_build),
            strip_prefix = jbr_runtime_archive_root(platform, runtime_build),
            excludes = ["**/.DS_Store"],
            executable_list = jbr_runtime_executable_list_label(platform) != None,
        )

    # the same watched inputs always produce the same repositories, so this stays out of MODULE.bazel.lock
    return module_ctx.extension_metadata(reproducible = True)

dev_launch_deps_community = module_extension(implementation = _dev_launch_deps_community_impl)
