load(":project_model_manifest.bzl", "write_project_model_manifest")

def _jps_to_bazel_targets_json_impl(ctx):
    output = ctx.actions.declare_file("bazel-targets.json")
    manifest = write_project_model_manifest(ctx, ctx.label.name + ".manifest", ctx.files.srcs, ctx.attr.mode)

    args = ctx.actions.args()
    args.add("--manifest=" + manifest.path)
    args.add("--output=" + output.path)
    args.add_all(ctx.attr.starlark_production_targets, format_each = "--starlark-production=%s")
    args.add_all(ctx.attr.starlark_test_targets, format_each = "--starlark-test=%s")
    args.add_all(ctx.attr.starlark_library_targets, format_each = "--starlark-library=%s")
    args.add_all(ctx.attr.starlark_iml_targets, format_each = "--starlark-iml=%s")
    args.add_all(ctx.attr.starlark_plugin_distribution_targets, format_each = "--starlark-plugin-distribution=%s")
    args.add_all(ctx.attr.starlark_plugin_content_report_files, format_each = "--starlark-plugin-content=%s")
    args.use_param_file("@%s", use_always = True)

    env = {}
    if ctx.attr.jps_to_bazel_treat_kotlin_dev_version_as_snapshot:
        env["JPS_TO_BAZEL_TREAT_KOTLIN_DEV_VERSION_AS_SNAPSHOT"] = ctx.attr.jps_to_bazel_treat_kotlin_dev_version_as_snapshot

    ctx.actions.run(
        inputs = ctx.files.srcs + [manifest],
        outputs = [output],
        executable = ctx.executable.tool,
        arguments = [args],
        env = env,
        mnemonic = "JpsToBazelTargetsJson",
        progress_message = "Generating bazel-targets.json for %s" % ctx.label,
    )

    return DefaultInfo(files = depset([output]))

jps_to_bazel_targets_json = rule(
    implementation = _jps_to_bazel_targets_json_impl,
    attrs = {
        "mode": attr.string(
            mandatory = True,
            values = ["community", "ultimate"],
            doc = "Build mode: 'community' for community-only, 'ultimate' for full project.",
        ),
        "srcs": attr.label_list(
            allow_files = True,
            doc = "JPS project model files",
        ),
        "tool": attr.label(
            executable = True,
            cfg = "exec",
            allow_files = False,
            mandatory = True,
            doc = "Hermetic bazel-targets.json generator executable.",
        ),
        "starlark_production_targets": attr.string_list(
            default = [],
            doc = "Starlark-derived production targets for parity assertion.",
        ),
        "starlark_test_targets": attr.string_list(
            default = [],
            doc = "Starlark-derived test targets for parity assertion.",
        ),
        "starlark_library_targets": attr.string_list(
            default = [],
            doc = "Starlark-derived library targets for parity assertion.",
        ),
        "starlark_iml_targets": attr.string_list(
            default = [],
            doc = "Starlark-derived IML targets for parity assertion.",
        ),
        "starlark_plugin_distribution_targets": attr.string_list(
            default = [],
            doc = "Starlark-derived plugin distribution targets for parity assertion.",
        ),
        "starlark_plugin_content_report_files": attr.string_list(
            default = [],
            doc = """Starlark-derived `plugin-content.yaml` file labels for parity assertion.

The inputs of the `contentTarget` half of `pluginDistributionTargets`, not the targets: whether a report yields a
content target depends on what the report says, which only the converter reads. Asserting the file set is what proves
the converter saw every report the checkout has, which is the property a missing manifest entry would break.""",
        ),
        "jps_to_bazel_treat_kotlin_dev_version_as_snapshot": attr.string(
            default = "",
            doc = "Kotlin dev version to treat as snapshot (forwarded as JPS_TO_BAZEL_TREAT_KOTLIN_DEV_VERSION_AS_SNAPSHOT env var to the tool).",
        ),
    },
)
