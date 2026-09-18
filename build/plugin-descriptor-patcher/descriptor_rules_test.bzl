"""Checks the executable, mode, and output of the descriptor actions."""

load("@bazel_skylib//lib:unittest.bzl", "analysistest", "asserts")

def _descriptor_action_test_impl(ctx):
    env = analysistest.begin(ctx)
    target = analysistest.target_under_test(env)
    actions = [action for action in analysistest.target_actions(env) if action.mnemonic == ctx.attr.mnemonic]
    asserts.equals(env, 1, len(actions))
    if actions:
        action = actions[0]
        asserts.equals(env, ctx.executable._patcher.path, action.argv[0])
        asserts.equals(env, ctx.attr.mode, action.argv[1])
        outputs = target[DefaultInfo].files.to_list()
        asserts.equals(env, [target.label.name + ".xml"], [file.basename for file in outputs])
        asserts.equals(env, outputs, action.outputs.to_list())
        asserts.equals(env, "--out=" + outputs[0].path, action.argv[2])
    return analysistest.end(env)

descriptor_action_test = analysistest.make(
    _descriptor_action_test_impl,
    attrs = {
        "mnemonic": attr.string(mandatory = True),
        "mode": attr.string(mandatory = True),
        "_patcher": attr.label(
            default = "//build/plugin-descriptor-patcher",
            executable = True,
            cfg = "exec",
        ),
    },
)
