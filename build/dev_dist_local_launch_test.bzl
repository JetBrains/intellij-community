"""Checks that local composition reads metadata and exposes the component runfiles."""

load("@bazel_skylib//lib:unittest.bzl", "analysistest", "asserts")
load("//platform/build-scripts/bazel-rules:intellij_dev_dist.bzl", "IntellijDevDistInfo")

def _local_launch_test_impl(ctx):
    env = analysistest.begin(ctx)
    target = analysistest.target_under_test(env)
    info = target[IntellijDevDistInfo]
    runtime_paths = {file.path: True for file in info.runtime_files.to_list()}
    actions = [action for action in analysistest.target_actions(env) if action.mnemonic == "IntellijDevLaunchMetadata"]
    asserts.equals(env, 1, len(actions))
    asserts.true(env, len(runtime_paths) > 0, "The local launch must expose component artifacts.")
    if actions:
        staged_payload = [file.path for file in actions[0].inputs.to_list() if file.path in runtime_paths]
        asserts.equals(env, [], staged_payload, "The metadata action must not stage the component artifacts.")
    runfiles = {file.path: True for file in target[DefaultInfo].default_runfiles.files.to_list()}
    asserts.equals(env, [], [path for path in runtime_paths if path not in runfiles], "Every component artifact must be a runfile.")
    asserts.equals(env, [], [action.mnemonic for action in analysistest.target_actions(env) if action.mnemonic == "IntellijDevDistCompose"])
    return analysistest.end(env)

dev_dist_local_launch_test = analysistest.make(_local_launch_test_impl)
