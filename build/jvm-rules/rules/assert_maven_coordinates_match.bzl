"""Analysis-time check that a target's `maven_coordinates=...` tag is exactly
in sync with the jar that target wraps.

Reads the `maven_coordinates=group:artifact:version` tag on the wrapped target,
constructs the expected jar basename `<artifact>-<version>.jar`, and compares it
byte-for-byte against the actual jar's basename (which comes from the
`http_file(downloaded_file_path = ...)` declaration in MODULE.bazel).

Fails the build with a pointed message if they diverge — catches the case where
someone bumps the version in MODULE.bazel without updating the
`maven_coordinates=` tag in BUILD.bazel, or vice versa.
"""

load("@rules_java//java:defs.bzl", "JavaInfo")

def _aspect_impl(target, ctx):
    tags = ctx.rule.attr.tags or []
    coord_tag = None
    for tag in tags:
        if tag.startswith("maven_coordinates="):
            coord_tag = tag[len("maven_coordinates="):]
            break
    if coord_tag == None:
        fail("%s: missing `maven_coordinates=...` tag (required by assert_maven_coordinates_match_jar)" % target.label)

    parts = coord_tag.split(":")
    if len(parts) != 3:
        fail("%s: malformed maven_coordinates tag %r (expected group:artifact:version)" %
             (target.label, coord_tag))
    _, artifact, tag_version = parts

    if JavaInfo not in target:
        fail("%s: target does not provide JavaInfo" % target.label)
    java_outputs = target[JavaInfo].java_outputs
    if not java_outputs:
        fail("%s: JavaInfo has no java_outputs to inspect" % target.label)
    basename = java_outputs[0].class_jar.basename

    expected = "%s-%s.jar" % (artifact, tag_version)
    if basename != expected:
        fail(("\n".join([
            "maven_coordinates tag does not match the jar basename on %s:",
            "  tag declares:  %s",
            "  expected jar:  %s",
            "  actual jar:    %s",
            "Either update the `maven_coordinates=` tag in BUILD.bazel or the matching",
            "http_file(downloaded_file_path = ...) / URL in MODULE.bazel so both agree.",
        ])) % (target.label, coord_tag, expected, basename))
    return []

_check_aspect = aspect(implementation = _aspect_impl)

def _impl(ctx):
    return [DefaultInfo()]

assert_maven_coordinates_match_jar = rule(
    implementation = _impl,
    attrs = {
        "target": attr.label(
            mandatory = True,
            providers = [JavaInfo],
            aspects = [_check_aspect],
            doc = "A jvm_import (or similar) target carrying a `maven_coordinates=...` tag.",
        ),
    },
    doc = "Fails analysis if the target's `maven_coordinates=...` tag version differs from the basename of the jar it wraps.",
)
