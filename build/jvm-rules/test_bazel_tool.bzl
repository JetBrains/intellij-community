# tools/test_bazel_tool.bzl

def _repo_impl(ctx):
    os_name = ctx.os.name.lower()
    arch = ctx.os.arch.lower()

    # Normalize OS
    if "linux" in os_name:
        os_key = "linux"
    elif "mac" in os_name or "darwin" in os_name:
        os_key = "darwin"
    elif "windows" in os_name:
        os_key = "windows"
    else:
        fail("Unsupported OS: " + os_name)

    # Normalize architecture
    if arch in ["amd64", "x86_64", "x64"]:
        arch_key = "x86_64"
    elif arch in ["arm64", "aarch64"]:
        arch_key = "arm64"
    else:
        fail("Unsupported architecture: " + arch)

    platform_key = os_key + "_" + arch_key

    urls = {
        "darwin_arm64": ctx.attr.url_darwin_arm64,
        "darwin_x86_64": ctx.attr.url_darwin_x86_64,
        "linux_arm64": ctx.attr.url_linux_arm64,
        "linux_x86_64": ctx.attr.url_linux_x86_64,
        "windows_arm64": ctx.attr.url_windows_arm64,
        "windows_x86_64": ctx.attr.url_windows_x86_64,
    }

    sha256s = {
        "darwin_arm64": ctx.attr.sha256_darwin_arm64,
        "darwin_x86_64": ctx.attr.sha256_darwin_x86_64,
        "linux_arm64": ctx.attr.sha256_linux_arm64,
        "linux_x86_64": ctx.attr.sha256_linux_x86_64,
        "windows_arm64": ctx.attr.sha256_windows_arm64,
        "windows_x86_64": ctx.attr.sha256_windows_x86_64,
    }

    url = urls.get(platform_key)
    sha256 = sha256s.get(platform_key)

    if not url:
        fail("No URL provided for platform: " + platform_key)
    if not sha256:
        fail("No sha256 provided for platform: " + platform_key)

    filename = url.split("/")[-1]

    ctx.download(
        url = url,
        output = filename,
        sha256 = sha256,
        executable = True,
    )

    ctx.file("BUILD.bazel", """
exports_files(["{filename}"])

alias(
    name = "bazel",
    actual = "{filename}",
    visibility = ["//visibility:public"],
)
""".format(filename = filename))

_repo = repository_rule(
    implementation = _repo_impl,
    attrs = {
        "url_darwin_arm64": attr.string(),
        "url_darwin_x86_64": attr.string(),
        "url_linux_arm64": attr.string(),
        "url_linux_x86_64": attr.string(),
        "url_windows_arm64": attr.string(),
        "url_windows_x86_64": attr.string(),
        "sha256_darwin_arm64": attr.string(),
        "sha256_darwin_x86_64": attr.string(),
        "sha256_linux_arm64": attr.string(),
        "sha256_linux_x86_64": attr.string(),
        "sha256_windows_arm64": attr.string(),
        "sha256_windows_x86_64": attr.string(),
    },
)

_config = tag_class(
    attrs = {
        "url_darwin_arm64": attr.string(),
        "url_darwin_x86_64": attr.string(),
        "url_linux_arm64": attr.string(),
        "url_linux_x86_64": attr.string(),
        "url_windows_arm64": attr.string(),
        "url_windows_x86_64": attr.string(),
        "sha256_darwin_arm64": attr.string(),
        "sha256_darwin_x86_64": attr.string(),
        "sha256_linux_arm64": attr.string(),
        "sha256_linux_x86_64": attr.string(),
        "sha256_windows_arm64": attr.string(),
        "sha256_windows_x86_64": attr.string(),
    },
)

def _extension_impl(mctx):
    cfg = mctx.modules[0].tags.config[0]
    _repo(
        name = "test_bazel_tool",
        url_darwin_arm64 = cfg.url_darwin_arm64,
        url_darwin_x86_64 = cfg.url_darwin_x86_64,
        url_linux_arm64 = cfg.url_linux_arm64,
        url_linux_x86_64 = cfg.url_linux_x86_64,
        url_windows_arm64 = cfg.url_windows_arm64,
        url_windows_x86_64 = cfg.url_windows_x86_64,
        sha256_darwin_arm64 = cfg.sha256_darwin_arm64,
        sha256_darwin_x86_64 = cfg.sha256_darwin_x86_64,
        sha256_linux_arm64 = cfg.sha256_linux_arm64,
        sha256_linux_x86_64 = cfg.sha256_linux_x86_64,
        sha256_windows_arm64 = cfg.sha256_windows_arm64,
        sha256_windows_x86_64 = cfg.sha256_windows_x86_64,
    )

extension = module_extension(
    implementation = _extension_impl,
    tag_classes = {
        "config": _config,
    },
)