"""The repository sets that a dev-mode assembly and IDE Starter read from runfiles, of the archives community owns.

A set maps each `HOST_PLATFORMS` entry to the download repositories of that platform. A reference of a product and the
legacy dev test read a set with `--preloaded-only`, so an undeclared URL fails them. A split distribution reads no
set: each component declares the archive file that it places as an input.

This file declares no repository. The extension of each owner declares its archives, beside the owner. The ultimate
sets are in `//build:dev_launch_dependencies.bzl`, and the two compose through [merge_repo_sets].
"""

load(":download_rules.bzl", "MANIFEST_NAME")
load(":host_platforms.bzl", "HOST_PLATFORMS")

# BuildDependenciesConstants.PRELOADED_DOWNLOADS_MANIFEST_PROPERTY
_MANIFEST_PROPERTY = "intellij.build.download.preloaded.manifest"

# BuildDependenciesConstants.PRELOADED_DOWNLOADS_ONLY_PROPERTY
_ONLY_PROPERTY = "intellij.build.download.preloaded.only"

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
        "libghostty_vt",
        "libwebp_natives",
        "bundled_maven",
        "bundled_maven3_libraries",
        "bundled_maven_telemetry_libraries",
        "restarter",
    ]),
    per_platform_repo_set(_label, lambda platform: "jcef_%s" % platform),
)

# Just the bundled Maven distribution and its libraries: what a test that opens a Maven project needs,
# because `MavenDistributionsCache` resolves the bundled Maven through `BundledMavenDownloader` and would
# otherwise download it. Deliberately not the whole dev-launch set - a test has no use for JCEF.
MAVEN_DEV_LAUNCH_REPOS = shared_repo_set(_label, ["bundled_maven", "bundled_maven3_libraries", "bundled_maven_telemetry_libraries"])

# What IDE Starter downloads to launch an IDE under test. Deliberately apart from the dev-launch set: a
# `bazel run` dev launch runs on the JVM Bazel gave it and never touches these 400 MB.
COMMUNITY_IDE_STARTER_REPOS = per_platform_repo_set(_label, lambda platform: "jbr_%s" % platform)
