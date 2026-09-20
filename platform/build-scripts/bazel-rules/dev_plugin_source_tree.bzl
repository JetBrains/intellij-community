"""The files of a source tree, listed at analysis time.

A source tree is one target whose files sit below a repository-relative prefix. The chain of `dev_plugin_remainder.bzl`
normalizes such a tree into a directory artifact, and `dev_plugin.bzl` copies each file of it to the plugin. Both name the
entries the same way: the file's path relative to the prefix, with the external workspace prefix stripped first.
"""

def strip_external_workspace_prefix(path):
    """A short path without its `../<repository>/` or `external/<repository>/` head, so it is repository-relative."""
    if path.startswith("../") or path.startswith("external/"):
        parts = path.split("/")
        if len(parts) < 3:
            fail("invalid external source path: %s" % path)
        return "/".join(parts[2:])
    return path

def source_tree_prefix(value, identifier):
    """The prefix of source tree `identifier`, checked to be a safe repository-relative path or empty."""
    if value == "":
        return value
    if (
        value.startswith("/") or "\\" in value or ":" in value or "\000" in value or
        any([part in ["", ".", ".."] for part in value.split("/")])
    ):
        fail("source tree %s has an unsafe repository-relative prefix: %r" % (identifier, value))
    return value

def source_tree_entry(file, prefix):
    """The entry of `file` in a source tree with `prefix`, or `None` when the file is not below the prefix."""
    path = strip_external_workspace_prefix(file.short_path)
    if prefix:
        prefix_with_separator = prefix + "/"
        if not path.startswith(prefix_with_separator):
            return None
        result = path[len(prefix_with_separator):]
    else:
        result = path
    if (
        not result or result.startswith("/") or "\\" in result or ":" in result or "\000" in result or "=" in result or
        any([part in ["", ".", ".."] for part in result.split("/")])
    ):
        fail("source tree entry is unsafe after stripping %s: %s" % (prefix, path))
    return result

def source_tree_entries(files, prefix, identifier, owner):
    """The entries of source tree `identifier`: a dict of entry to the regular `File` of `files` it names.

    `owner` is the label of the target that lists `files`; a failure message names it. Fails on a directory artifact, on
    two files with one entry, on an entry that is an ancestor of another entry, and on no file below the prefix.
    """
    entries = {}
    for file in files:
        if file.is_directory:
            fail("source tree %s accepts regular declared Files only, got directory %s" % (identifier, file.path))
        entry = source_tree_entry(file, prefix)
        if entry == None:
            continue
        if entry in entries:
            fail("source tree %s has conflicting entry %s from %s and %s" % (identifier, entry, entries[entry].path, file.path))
        entries[entry] = file
    if not entries:
        fail("source tree %s has no declared File below %s in %s" % (identifier, prefix, owner))
    for entry in sorted(entries.keys()):
        parts = entry.split("/")
        for size in range(1, len(parts)):
            ancestor = "/".join(parts[:size])
            if ancestor in entries:
                fail("source tree %s has conflicting file entries %s and %s" % (identifier, ancestor, entry))
    return entries
