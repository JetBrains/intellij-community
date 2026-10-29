"""The repository rules that fetch a downloaded build dependency, and the code they run.

An extension of the owner of an archive calls these rules. The owner derives each URL from the version that a file of
the checkout pins, so a version bump refetches only the repository that it changed. A test dependency repository of
`test_deps_extension.bzl` writes its files the same way.

This file defines the identity of every download repository: Bazel keys a repository by the digest of the file that
defines its rule and of everything that file loads. So this file loads only `@bazel_tools`, and a helper that an
extension needs goes into `download_helpers.bzl`.
"""

load("@bazel_tools//tools/build_defs/repo:cache.bzl", "DEFAULT_CANONICAL_ID_ENV", "get_default_canonical_id")
load("@bazel_tools//tools/build_defs/repo:utils.bzl", "get_auth")

# The manifest that `PreloadedDownloadsManifest` reads
MANIFEST_NAME = "preloaded-downloads-v1.tsv"

_MANIFEST_HEADER = "intellij-build-downloads\t1"

# The executable list of an `extracted_download_repo`. See `_executable_files`.
EXECUTABLE_LIST_NAME = "executables.json"

def write_downloads_repo(repository_ctx, files):
    """Fetches every declared file and materializes the repository the build sees.

    `files` is a list of `struct(name, url, sha256)`. An empty `sha256` downloads unpinned, which
    only makes sense for a repository whose URLs are derived from versions the checkout owns: such
    a repository is refetched exactly when its artifact changed, so there is no cached copy a
    checksum could have spared us. The manifest records the hash Bazel observed either way, so the
    runtime keeps its content-addressed lookup.

    Returns whether every file was checksum-pinned before the download - which is what makes the
    result reproducible, and the repository shareable through the repo contents cache.
    """
    conflict = find_download_conflict(files)
    if conflict:
        fail(conflict)

    downloads = []
    for f in files:
        # Without a canonical id, any cache entry whose checksum matches satisfies the request, whatever
        # URL put it there. Bazel recommends one wherever a checksum is given, and an unpinned entry gets
        # the same treatment so that its cache hit is still tied to the URL it came from.
        downloads.append(repository_ctx.download(
            url = f.url,
            output = f.name,
            sha256 = f.sha256,
            block = False,
            canonical_id = get_default_canonical_id(repository_ctx, [f.url]),
            auth = get_auth(repository_ctx, [f.url]),
        ))

    pinned = True
    rows = []
    for index, download in enumerate(downloads):
        f = files[index]
        if not f.sha256:
            pinned = False
        rows.append("%s\t%s\t%s" % (f.name, download.wait().sha256, f.url))

    repository_ctx.file(MANIFEST_NAME, manifest_content(rows))
    names = [f.name for f in files]
    repository_ctx.file(
        "BUILD",
        """
package(default_visibility = ["//visibility:public"])
exports_files([
{exported}
])
filegroup(
    name = "files",
    srcs = [
{artifacts}
    ],
)
""".format(
            exported = ",\n".join(["  \"%s\"" % name for name in names + [MANIFEST_NAME]]),
            artifacts = "\n".join(["        \"%s\"," % name for name in names]),
        ),
    )
    return pinned

def manifest_content(rows):
    """The preloaded-downloads manifest holding [rows], each already `name<tab>sha256<tab>url`.

    Joined with the header rather than concatenated after it, because a repository that declares no file
    has no rows, and concatenation would leave a blank line that `PreloadedDownloads.parse` rejects.
    """
    return "\n".join([_MANIFEST_HEADER] + rows) + "\n"

def find_download_conflict(files):
    """Returns an error for duplicate output names or URLs, or `None` when [files] are safe to fetch."""
    names = set()
    urls = set()
    for f in files:
        if f.name in names:
            return "download repository has duplicate output file name: %s" % f.name
        if f.url in urls:
            return "download repository has duplicate URL: %s" % f.url
        names.add(f.name)
        urls.add(f.url)
    return None

def all_downloads_pinned(files):
    """Whether every file has a checksum known before it is downloaded."""
    return all([f.sha256 for f in files])

def _file_name(url):
    name = url.rpartition("/")[2]
    if not name or "?" in name or "#" in name:
        fail("cannot derive a repository file name from '%s'" % url)
    return name

def _download_repo_impl(repository_ctx):
    urls = repository_ctx.attr.urls
    sha256s = repository_ctx.attr.sha256s
    if sha256s and len(sha256s) != len(urls):
        fail("download_repo got %d URLs but %d checksums" % (len(urls), len(sha256s)))
    files = [
        struct(name = _file_name(url), url = url, sha256 = sha256s[index] if sha256s else "")
        for index, url in enumerate(urls)
    ]
    write_downloads_repo(repository_ctx, files)

    # Reproducible even where the checksum was not known up front: every URL here carries its own
    # version, so the same URL is the same artifact, and the repo contents cache may share it.
    # The one exception is `jetbrains_public_keys`, whose URL carries no version.
    return repository_ctx.repo_metadata(reproducible = all_downloads_pinned(files))

download_repo = repository_rule(
    doc = """Files that a build reads as they are downloaded, with the preloaded-downloads manifest beside them.

    The repository exports each file, the `files` filegroup of all of them, and `preloaded-downloads-v1.tsv`.
    """,
    implementation = _download_repo_impl,
    environ = [DEFAULT_CANONICAL_ID_ENV],
    attrs = {
        # optional, and only where the checkout already owns a trustworthy hash - see write_downloads_repo
        "sha256s": attr.string_list(),
        "urls": attr.string_list(mandatory = True),
    },
)

def _executable_files(repository_ctx):
    """The sorted paths of the extracted files with an executable mode bit, or `None` on a Windows host.

    Only the fetch sees the modes of the archive. Bazel can restore the repository from the remote repo contents cache,
    and then it writes each file that an action reads with the mode `0555`. The list is part of the repository
    contents, so it keeps the modes of the archive. A Windows file system has no executable bit, so the list is unknown
    there. `-L` follows a link, as the collector does.
    """
    if repository_ctx.os.name.startswith("windows"):
        return None
    result = repository_ctx.execute(["find", "-L", ".", "-type", "f", "(", "-perm", "-100", "-o", "-perm", "-010", "-o", "-perm", "-001", ")"])
    if result.return_code != 0:
        fail("cannot list the executable files of %s: %s" % (repository_ctx.attr.url, result.stderr))
    return sorted([line.removeprefix("./") for line in result.stdout.splitlines() if line])

def _extracted_download_repo_impl(repository_ctx):
    urls = [repository_ctx.attr.url]
    result = repository_ctx.download_and_extract(
        url = urls,
        sha256 = repository_ctx.attr.sha256,
        strip_prefix = repository_ctx.attr.strip_prefix,
        canonical_id = get_default_canonical_id(repository_ctx, urls),
        auth = get_auth(repository_ctx, urls),
    )
    exported = []
    executables = None
    if repository_ctx.attr.executable_list:
        executables = _executable_files(repository_ctx)
        repository_ctx.file(EXECUTABLE_LIST_NAME, json.encode({"executables": executables}) + "\n", executable = False)
        exported.append(EXECUTABLE_LIST_NAME)
    repository_ctx.file(
        "BUILD",
        """
package(default_visibility = ["//visibility:public"])
files = glob(["**"], exclude = %s, allow_empty = False)
exports_files(files + %s)
filegroup(
    name = "files",
    srcs = files,
)
""" % (repr(["BUILD", "BUILD.bazel", "REPO.bazel"] + exported + repository_ctx.attr.excludes), repr(exported)),
    )

    # A repository with an unknown executable list stays out of the repo contents cache, so a Windows fetch cannot
    # hand it to a macOS or Linux host. A repository without a list stays reproducible on every host.
    pinned = bool(repository_ctx.attr.sha256) or bool(result.sha256)
    known_list = not repository_ctx.attr.executable_list or executables != None
    return repository_ctx.repo_metadata(reproducible = pinned and known_list)

extracted_download_repo = repository_rule(
    doc = """An archive a build needs *unpacked*, extracted once by Bazel instead of by the build.

    [download_repo] takes the network out of an assembly: the archive arrives as a declared input and the
    downloader reads it from there. An archive whose *contents* the build reads is extracted here, and the consumer
    is handed files rather than a directory it has to populate. The `excludes` globs drop the junk entries an
    archive carries, such as `.DS_Store`.

    With `executable_list`, the repository also exports its executable list, `executables.json`, apart from
    `files`. The list names each file that the archive gives an executable mode bit, as `{"executables": [<path>, ...]}`.
    On a Windows host the value is `null`, and the repository is not reproducible.
    """,
    implementation = _extracted_download_repo_impl,
    environ = [DEFAULT_CANONICAL_ID_ENV],
    attrs = {
        # globs of the extracted files that the `files` filegroup leaves out, beside the BUILD files and the `REPO.bazel`
        # boundary file that Bazel writes into the repository
        "excludes": attr.string_list(),
        # writes and exports `executables.json`. Only a repository whose list the collector reads sets it,
        # because a fetch on a Windows host then keeps the repository out of the repo contents cache.
        "executable_list": attr.bool(default = False),
        # optional, exactly as in download_repo: these URLs carry their version, so the same URL is the same
        # artifact and an unpinned fetch costs a re-download only when the version moves
        "sha256": attr.string(),
        "strip_prefix": attr.string(),
        "url": attr.string(mandatory = True),
    },
)

def text_repo_files(file_name, content):
    """The files of a [text_repo], by path: `file_name` with `content`, and a BUILD that exports it."""
    return {
        "BUILD": 'package(default_visibility = ["//visibility:public"])\n\nexports_files(["%s"])\n' % file_name,
        file_name: content,
    }

def _text_repo_impl(repository_ctx):
    for path, content in text_repo_files(repository_ctx.attr.file_name, repository_ctx.attr.content).items():
        repository_ctx.file(path, content, executable = False)
    return repository_ctx.repo_metadata(reproducible = True)

text_repo = repository_rule(
    doc = """One file that holds a pinned value as text, byte for byte, such as a version a plugin ships.

    Not a download: it has no preloaded-downloads manifest and joins no dev-launch set. A plugin component reads it as a
    declared input, so the layout of the plugin copies a file instead of running the production generator. The
    extension passes the value as `content`, so a bump of it refetches this repository and no other.
    """,
    implementation = _text_repo_impl,
    attrs = {
        "content": attr.string(mandatory = True),
        "file_name": attr.string(mandatory = True),
    },
)
