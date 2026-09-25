"""Checks the executable, mode, and output of the descriptor actions."""

load("@bazel_skylib//lib:unittest.bzl", "analysistest", "asserts")

def _descriptor_action_test_impl(ctx):
    env = analysistest.begin(ctx)
    target = analysistest.target_under_test(env)
    actions = [action for action in analysistest.target_actions(env) if action.mnemonic == ctx.attr.mnemonic]
    asserts.equals(env, 1, len(actions))
    if actions:
        action = actions[0]
        asserts.equals(env, ctx.executable._writer.path, action.argv[0])
        asserts.equals(env, ctx.attr.mode, action.argv[1])
        outputs = target[DefaultInfo].files.to_list()
        asserts.equals(env, [target.label.name + ".xml"], [file.basename for file in outputs])
        extra_outputs = [file for file in action.outputs.to_list() if file not in outputs]
        asserts.equals(env, outputs, [file for file in action.outputs.to_list() if file in outputs])
        asserts.equals(env, [target.label.name + suffix for suffix in ctx.attr.extra_output_suffixes], [file.basename for file in extra_outputs])
        asserts.equals(env, "--out=" + outputs[0].path, action.argv[2])
    return analysistest.end(env)

descriptor_action_test = analysistest.make(
    _descriptor_action_test_impl,
    attrs = {
        "mnemonic": attr.string(mandatory = True),
        "mode": attr.string(mandatory = True),
        "extra_output_suffixes": attr.string_list(doc = "The suffixes of the outputs beside the descriptor, in action order."),
        "_writer": attr.label(
            default = "//platform/build-scripts/bazel-rules:plugin_descriptor_writer",
            executable = True,
            cfg = "exec",
        ),
    },
)
