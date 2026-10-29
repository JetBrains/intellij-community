"""Helpers for the module extensions that declare downloaded build dependencies.

An extension reads the version of an archive out of the file that already owns it, and derives the URL from it. A
version bump then changes one pinned file, and no `.bzl` file copies the version. Only extensions load this file; a
repository rule does not, so an edit here changes no repository identity.
"""

# BuildDependenciesConstants
MAVEN_CENTRAL_URL = "https://cache-redirector.jetbrains.com/repo.maven.apache.org/maven2"
INTELLIJ_DEPENDENCIES_URL = "https://cache-redirector.jetbrains.com/intellij-dependencies"
INTELLIJ_JBR_URL = "https://cache-redirector.jetbrains.com/intellij-jbr"
JETBRAINS_FILES_URL = "https://cache-redirector.jetbrains.com/files/p/ij/intellij-dependencies"
JETBRAINS_MAVEN_URL = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies"
INTELLIJ_BUILD_DEPENDENCIES_URL = "https://cache-redirector.jetbrains.com/files/p/ij/intellij-build-dependencies"

# OsFamily.jbrArchiveSuffix
JBR_OS_SUFFIX = {
    "darwin": "osx",
    "linux": "linux",
    "windows": "windows",
}

def read_properties(module_ctx, label):
    """The `key=value` lines of the properties file [label]. The extension watches the file."""
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
    """The value of [key] in [properties], read from [label]. A missing or empty value fails the extension."""
    value = properties.get(key)
    if not value:
        fail("'%s' is missing from %s" % (key, label))
    return value

def kotlin_const(module_ctx, label, name):
    """The value of a `const val <name> = "..."` or `const val <name>: String = "..."` in the Kotlin source file [label]."""
    source = module_ctx.read(label, watch = "yes")
    for marker in ["const val %s = \"" % name, "const val %s: String = \"" % name]:
        start = source.find(marker)
        if start >= 0:
            break
    if start < 0:
        fail("cannot find 'const val %s' in %s" % (name, label))
    start += len(marker)
    end = source.find("\"", start)
    if end < 0:
        fail("'const val %s' in %s is not terminated" % (name, label))
    return source[start:end]

def iml_library_version(module_ctx, label, marker):
    """The version of a module library in the .iml [label]: the text between [marker] and the closing quote."""
    iml = module_ctx.read(label, watch = "yes")
    start = iml.find(marker)
    if start < 0:
        fail("cannot find '%s' in %s" % (marker, label))
    start += len(marker)
    end = iml.find("\"", start)
    if end < 0:
        fail("the maven-id after '%s' in %s is not terminated" % (marker, label))
    return iml[start:end]

def json_manifest_entry(module_ctx, label, key, field, name):
    """The entry of the list [key] in the JSON file [label] whose [field] is [name], with the whole manifest."""
    manifest = json.decode(module_ctx.read(label, watch = "yes"))
    for entry in manifest[key]:
        if entry[field] == name:
            return struct(manifest = manifest, entry = entry)
    fail("'%s' is missing from %s" % (name, label))

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
    """The jar URLs of comma-separated `group:artifact:version` [coordinates]."""
    urls = []
    for coordinate in coordinates.split(","):
        parts = coordinate.strip().split(":")
        if len(parts) != 3:
            fail("Maven coordinates must have group, artifact, and version: '%s'" % coordinate)
        urls.append(maven_url(repository, parts[0], parts[1], parts[2], "jar"))
    return urls
