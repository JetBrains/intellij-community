load("@bazel_tools//tools/build_defs/repo:cache.bzl", "CANONICAL_ID_DOC", "DEFAULT_CANONICAL_ID_ENV",  "get_default_canonical_id")
load("@bazel_tools//tools/build_defs/repo:utils.bzl", "get_auth")

_HTTP_JAR_BUILD = """\
load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_import")

kt_jvm_import(
  name = "jar",
  jar = "{jar}",
  visibility = ["//visibility:public"],
)
"""

def _http_jvm_lib(ctx):
  url = ctx.attr.url
  urls = [url]
  downloaded_file_name = ctx.attr.downloaded_file_name
  if len(downloaded_file_name) == 0:
    downloaded_file_name = url.split("/")[-1]

  if not downloaded_file_name.endswith(".jar"):
    fail("downloaded_file_name must have .jar extension")

  ctx.download(
    urls,
    downloaded_file_name,
    ctx.attr.sha256,
    canonical_id = get_default_canonical_id(ctx, urls),
    auth = get_auth(ctx, urls),
  )

  ctx.file("BUILD", _HTTP_JAR_BUILD.format(
    jar = downloaded_file_name,
  ))

  return None

_http_jar_attrs = {
  "sha256": attr.string(mandatory = True),
  "url": attr.string(mandatory = True),
  "netrc": attr.string(doc = "Location of the .netrc file to use for authentication"),
  "auth_patterns": attr.string_dict(),
  "downloaded_file_name": attr.string(
    default = "",
    doc = "Filename assigned to the jar downloaded",
  ),
}

http_jvm_lib = repository_rule(
  implementation = _http_jvm_lib,
  attrs = _http_jar_attrs,
  environ = [DEFAULT_CANONICAL_ID_ENV],
)