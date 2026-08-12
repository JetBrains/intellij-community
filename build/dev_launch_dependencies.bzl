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

A repository is per platform only where the artifact is: the JBR, JCEF, the Chatter binary and the
Toolbox daemon are, while IJent, libwebp, libghostty-vt and the bundled Maven distribution ship every
platform in one archive and are fetched once for all six.

This file owns the community half - the machinery, and the groups whose version lives under
`community/`. The ultimate half is `//build:dev_launch_dependencies.bzl`, and the two compose through
[merge_repo_sets].
"""

load(":test_deps_extension.bzl", "write_downloads_repo")

# BuildDependenciesConstants
MAVEN_CENTRAL_URL = "https://cache-redirector.jetbrains.com/repo.maven.apache.org/maven2"
INTELLIJ_DEPENDENCIES_URL = "https://cache-redirector.jetbrains.com/intellij-dependencies"
INTELLIJ_JBR_URL = "https://cache-redirector.jetbrains.com/intellij-jbr"

# Named after the `host_*` config settings in this package, which are the select axis every consumer uses.
HOST_PLATFORMS = [
    "darwin_aarch64",
    "darwin_x64",
    "linux_aarch64",
    "linux_x64",
    "windows_aarch64",
    "windows_x64",
]

# OsFamily.jbrArchiveSuffix
JBR_OS_SUFFIX = {
    "darwin": "osx",
    "linux": "linux",
    "windows": "windows",
}

_COMMUNITY_DEPENDENCIES = Label("//build:dependencies/dependencies.properties")

MANIFEST_NAME = "preloaded-downloads-v1.tsv"

# BuildDependenciesConstants.PRELOADED_DOWNLOADS_MANIFEST_PROPERTY
_MANIFEST_PROPERTY = "intellij.build.download.preloaded.manifest"

# BuildDependenciesConstants.PRELOADED_DOWNLOADS_ONLY_PROPERTY
_ONLY_PROPERTY = "intellij.build.download.preloaded.only"

def platform_parts(platform):
    """Splits a [HOST_PLATFORMS] entry into its OS and architecture tokens."""
    if platform not in HOST_PLATFORMS:
        fail("'%s' is not one of %s" % (platform, HOST_PLATFORMS))
    os, _, arch = platform.rpartition("_")
    return struct(os = os, arch = arch)

def read_properties(module_ctx, label):
    result = {}
    for line in module_ctx.read(label, watch = "yes").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or line.startswith("!"):
            continue
        key, separator, value = line.partition("=")
        if not separator:
            continue
        result[key.strip()] = value.strip()
    return result

def pinned(properties, label, key):
    value = properties.get(key)
    if not value:
        fail("'%s' is missing from %s" % (key, label))
    return value

# BuildDependenciesDownloader.getUriForMavenArtifact
def maven_url(repository, group_id, artifact_id, version, packaging, classifier = None):
    return "%s/%s/%s/%s/%s-%s%s.%s" % (
        repository,
        group_id.replace(".", "/"),
        artifact_id,
        version,
        artifact_id,
        version,
        "-" + classifier if classifier else "",
        packaging,
    )

def maven_coordinates_urls(repository, coordinates):
    urls = []
    for coordinate in coordinates.split(","):
        parts = coordinate.strip().split(":")
        if len(parts) != 3:
            fail("Maven coordinates must have group, artifact, and version: '%s'" % coordinate)
        urls.append(maven_url(repository, parts[0], parts[1], parts[2], "jar"))
    return urls

# JBRResolver.jbrArchiveFileName
def jbr_url(platform, runtime_build):
    parts = platform_parts(platform)
    major_version, separator, build_number = runtime_build.partition("b")
    if not separator or not major_version or not build_number:
        fail("'runtimeBuild' does not match '<update>b<build_number>': '%s'" % runtime_build)
    return "%s/jbrsdk_jcef-%s-%s-%s-b%s.tar.gz" % (
        INTELLIJ_JBR_URL,
        major_version,
        JBR_OS_SUFFIX[parts.os],
        parts.arch,
        build_number,
    )

# CommunityRepositoryModules.jcefDownloadUrl
def jcef_url(platform, jcef_build):
    parts = platform_parts(platform)
    return "%s/jcef-%s-%s-%s.tar.gz" % (INTELLIJ_JBR_URL, JBR_OS_SUFFIX[parts.os], parts.arch, jcef_build)

# TerminalLibGhosttyVtDownloader.downloadUrl
def lib_ghostty_vt_url(version):
    return "https://packages.jetbrains.team/files/p/ij/intellij-build-dependencies/libghostty-vt/%s/libghostty-vt.zip.zst" % version

def _file_name(url):
    name = url.rpartition("/")[2]
    if not name or "?" in name or "#" in name:
        fail("cannot derive a repository file name from '%s'" % url)
    return name

def _repo_impl(repository_ctx):
    urls = repository_ctx.attr.urls
    sha256s = repository_ctx.attr.sha256s
    if sha256s and len(sha256s) != len(urls):
        fail("dev_launch_deps got %d URLs but %d checksums" % (len(urls), len(sha256s)))
    files = [
        struct(name = _file_name(url), url = url, sha256 = sha256s[index] if sha256s else "")
        for index, url in enumerate(urls)
    ]
    write_downloads_repo(repository_ctx, files)

    # Reproducible even where the checksum was not known up front: every URL here carries its own
    # version, so the same URL is the same artifact, and the repo contents cache may share it.
    return repository_ctx.repo_metadata(reproducible = True)

dev_launch_deps_repo = repository_rule(
    implementation = _repo_impl,
    attrs = {
        # optional, and only where the checkout already owns a trustworthy hash - see write_downloads_repo
        "sha256s": attr.string_list(),
        "urls": attr.string_list(mandatory = True),
    },
)

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

def _per_platform_repos(name):
    return {platform: [_entry("dev_launch_%s_%s" % (platform, name))] for platform in HOST_PLATFORMS}

def _shared_repos(names):
    return {platform: [_entry("dev_launch_" + name) for name in names] for platform in HOST_PLATFORMS}

def _entry(repo):
    # Resolved here, in the module that declares the repository: `Label` reads the repo mapping of the
    # .bzl it is written in, so a set built elsewhere must build its own labels. See the ultimate half.
    return struct(files = Label("@%s//:files" % repo), manifest = Label("@%s//:%s" % (repo, MANIFEST_NAME)))

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

def preloaded_downloads_flag(repo_set, platforms = None):
    """`-Dintellij.build.download.preloaded.manifest` for [preloaded_downloads_manifest_data].

    Comma-joined, never space-joined: the whole thing has to survive as one token in `jvm_flags`.
    """
    return _select_by_platform(repo_set, platforms, _manifest_flag)

def preloaded_downloads_only_flag():
    """Makes the configured manifests the complete inventory, so an undeclared URL fails.

    For a target that must not reach the network: a sandboxed test, or a worker running off a read-only
    share of the checkout. An ordinary dev launch wants the opposite and does not pass this, because no
    shared set can enumerate what every product asks for. Needs no `select`: it constrains the manifests
    a target was given, and where there are none it has nothing to forbid.
    """
    return ["-D%s=true" % _ONLY_PROPERTY]

# What a dev-mode assembly downloads, of the groups community owns.
COMMUNITY_DEV_LAUNCH_REPOS = merge_repo_sets(
    _shared_repos(["libghostty", "libwebp", "maven"]),
    _per_platform_repos("jcef"),
)

# What IDE Starter downloads to launch an IDE under test. Deliberately apart from the dev-launch set: a
# `bazel run` dev launch runs on the JVM Bazel gave it and never touches these 400 MB.
COMMUNITY_IDE_STARTER_REPOS = _per_platform_repos("jbr")

def _dev_launch_deps_community_impl(module_ctx):
    community = read_properties(module_ctx, _COMMUNITY_DEPENDENCIES)

    dev_launch_deps_repo(
        name = "dev_launch_libghostty",
        urls = [lib_ghostty_vt_url(pinned(community, _COMMUNITY_DEPENDENCIES, "libGhosttyVtVersion"))],
    )
    dev_launch_deps_repo(
        name = "dev_launch_libwebp",
        urls = [maven_url(
            INTELLIJ_DEPENDENCIES_URL,
            "org.jetbrains.intellij.deps",
            "libwebp",
            pinned(community, _COMMUNITY_DEPENDENCIES, "libwebpVersion"),
            "tar.gz",
        )],
    )
    dev_launch_deps_repo(
        name = "dev_launch_maven",
        urls = [maven_url(
            MAVEN_CENTRAL_URL,
            "org.apache.maven",
            "apache-maven",
            pinned(community, _COMMUNITY_DEPENDENCIES, "bundledMavenVersion"),
            "zip",
            classifier = "bin",
        )] + maven_coordinates_urls(
            MAVEN_CENTRAL_URL,
            pinned(community, _COMMUNITY_DEPENDENCIES, "bundledMaven3Libraries"),
        ) + maven_coordinates_urls(
            MAVEN_CENTRAL_URL,
            pinned(community, _COMMUNITY_DEPENDENCIES, "bundledMavenTelemetryLibraries"),
        ),
    )

    jcef_build = pinned(community, _COMMUNITY_DEPENDENCIES, "jcefBuild")
    runtime_build = pinned(community, _COMMUNITY_DEPENDENCIES, "runtimeBuild")
    for platform in HOST_PLATFORMS:
        dev_launch_deps_repo(
            name = "dev_launch_%s_jcef" % platform,
            urls = [jcef_url(platform, jcef_build)],
        )
        dev_launch_deps_repo(
            name = "dev_launch_%s_jbr" % platform,
            urls = [jbr_url(platform, runtime_build)],
        )

    # the same watched inputs always produce the same repositories, so this stays out of MODULE.bazel.lock
    return module_ctx.extension_metadata(reproducible = True)

dev_launch_deps_community = module_extension(implementation = _dev_launch_deps_community_impl)
