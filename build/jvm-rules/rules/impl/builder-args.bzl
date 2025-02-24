load("@bazel_skylib//rules:common_settings.bzl", "BuildSettingInfo")
load("@rules_java//java:defs.bzl", "JavaInfo")
load("//:rules/impl/associates.bzl", "get_associates")
load("//:rules/impl/kotlinc-options.bzl", "KotlincOptions", "kotlinc_options_to_flags")

visibility("private")

def init_builder_args(ctx, rule_kind, associates, transitiveInputs, plugins, compile_deps):
    """Initialize an arg object for a task that will be executed by the Kotlin Builder."""
    args = ctx.actions.args()
    args.set_param_file_format("multiline")
    args.use_param_file("--flagfile=%s", use_always = True)

    args.add("--target_label", ctx.label)
    args.add("--rule_kind", rule_kind)
    args.add("--kotlin_module_name", associates.module_name)

    kotlinc_options = ctx.attr.kotlinc_opts[KotlincOptions]

    if kotlinc_options.report_unused_deps:
        deps_artifacts = _collect_jdeps_from_artifacts(ctx.attr.deps + associates.targets)
        transitiveInputs.append(deps_artifacts)
        args.add_all("--deps_artifacts", deps_artifacts)

    if associates:
        args.add_all("--friends", associates.jars, map_each = _flatten_jars)

    if ctx.attr._trace[BuildSettingInfo].value:
        args.add("--trace")

    kotlinc_options_to_flags(kotlinc_options, args)

    args.add_all("--opt-in", kotlinc_options.opt_in)
    args.add_all("--cp", compile_deps.compile_jars)

    if ctx.attr._reduced_classpath:
        args.add("--reduced-classpath-mode", "true")
        args.add_all("--direct-dependencies", depset(transitive = [j.compile_jars for j in compile_deps.deps]))

    for id, classpath in plugins.compile_phase.classpath.items():
        args.add("--plugin-id", id)
        args.add_joined("--plugin-classpath", classpath, omit_if_empty = False, join_with = ":")
        transitiveInputs.append(classpath)

    return args

def _collect_jdeps_from_artifacts(all_targets):
    """Collect Jdeps artifacts if required."""

    # collect `.jdeps` outputs from targets that contain JavaInfo and have a `jdeps` output
    return depset([t[JavaInfo].outputs.jdeps for t in all_targets if JavaInfo in t and t[JavaInfo].outputs.jdeps])

def _flatten_jars(nested_jars_depset):
    """Returns a list of strings containing the compile_jars for depset of targets.

    This ends up unwinding the nesting of depsets, since compile_jars contains depsets inside
    the nested_jars targets, which themselves are depsets.  This function is intended to be called
    lazily form within Args.add_all(map_each) as it collapses depsets.
    """
    compile_jars_depsets = [
        target[JavaInfo].compile_jars
        for target in nested_jars_depset.to_list()
        if target[JavaInfo].compile_jars
    ]
    return [file.path for file in depset(transitive = compile_jars_depsets).to_list()]
