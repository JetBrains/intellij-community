"""Generates intellij_add_opens.bzl with `INTELLIJ_ADD_OPENS` from OpenedPackages.txt, the file the installers read."""

_OPENED_PACKAGES_FILE = Label("//platform/platform-impl:resources/META-INF/OpenedPackages.txt")

_PREFIX = "--add-opens="
_SUFFIX = "=ALL-UNNAMED"

def parse_intellij_add_opens(content):
    """Returns the `<module>/<package>` entries of OpenedPackages.txt content, in file order."""
    packages = []
    for line in content.splitlines():
        # `JavaModuleOptions.readOptions` passes each line to the JVM as is, so a blank or malformed line
        # is an error here rather than a JVM failure at launch.
        if not line.startswith(_PREFIX) or not line.endswith(_SUFFIX):
            fail("%s: expected `%s<module>/<package>%s`, got `%s`" % (_OPENED_PACKAGES_FILE, _PREFIX, _SUFFIX, line))
        packages.append(line[len(_PREFIX):-len(_SUFFIX)])
    return packages

def generate_intellij_add_opens_bzl(packages):
    lines = ["# Generated from OpenedPackages.txt. Edit the text file, not this one.", "INTELLIJ_ADD_OPENS = ["]
    for package in packages:
        lines.append('    "%s",' % package)
    lines.append("]")
    return "\n".join(lines) + "\n"

def _intellij_add_opens_repo_impl(ctx):
    content = ctx.read(_OPENED_PACKAGES_FILE, watch = "yes")
    ctx.file("intellij_add_opens.bzl", generate_intellij_add_opens_bzl(parse_intellij_add_opens(content)))
    ctx.file("BUILD", 'exports_files(["intellij_add_opens.bzl"])')
    return ctx.repo_metadata(reproducible = True)

intellij_add_opens_repo = repository_rule(
    implementation = _intellij_add_opens_repo_impl,
)

def _extension_impl(module_ctx):
    intellij_add_opens_repo(name = "intellij_add_opens")

    # A pure function of the checkout: keep the extension out of MODULE.bazel.lock (see jps_dynamic_deps_community.bzl).
    return module_ctx.extension_metadata(reproducible = True)

intellij_add_opens_extension = module_extension(
    implementation = _extension_impl,
)
