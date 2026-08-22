"""How `content_module_jar` finds the tool that packs it.

The tool is behind a flag rather than named directly because the implementations do not have the same *action shape*.
A JVM packer is `java` plus a list of VM flags, a launcher source and a whole runtime tree in `tools`; a native packer
is one executable with no flags and no tools beside itself. A `label_flag` pointing at a *file* cannot express that
difference, so the flag points at a target that carries its own shape in a provider and the rule reads it.

This also keeps the dependency direction legal. `rules_jvm` is a module of its own, shipped as a consumable archive,
and it may not name a label in the repository that consumes it - so the default here is deliberately a stub, and the
consuming repository points the flag at its own implementation from `.bazelrc`.
"""

# Public because the repository that consumes these rules declares its own implementation with
# `native_content_module_packer`, and it has to be able to load this file to do so.
visibility("public")

ContentModulePackerInfo = provider(
    doc = "An implementation of the content-module packer, with everything the action needs to run it.",
    fields = {
        "executable": "The `File` or exec-path string to run.",
        "argument_prefix": "Arguments that precede the flag file - VM flags and a launcher, for a JVM implementation.",
        "tools": "A `depset` of every file the executable needs at runtime.",
        "execution_requirements": "The action's execution requirements, since a worker declares what a one-shot does not.",
    },
)

def _native_content_module_packer_impl(ctx):
    return [ContentModulePackerInfo(
        executable = ctx.executable.binary,
        argument_prefix = [],
        # A static binary needs nothing beside itself. This is the whole difference from a JVM implementation, which
        # declares its runtime tree - 159 files - as an input of every packing action in the repository.
        tools = depset([ctx.executable.binary], transitive = [ctx.attr.binary[DefaultInfo].default_runfiles.files]),
        # No worker keys: the process is the action. `supports-path-mapping` is worker-only and path mapping is not
        # enabled in this repository anyway, and `supports-multiplex-sandboxing` is inert without `--worker_sandboxing`.
        #
        # `no-sandbox` is not an optimisation, it is parity. The worker this replaces ran *non-sandboxed* - Bazel's
        # worker strategy behaves like `local` unless `--worker_sandboxing` is set, and it is set nowhere here - so
        # without this the rewrite silently adds a sandbox per jar that the JVM never paid for. Measured on this
        # repository at 2 524 jars: 59.4 s sandboxed against 29.5 s for the worker, which is the whole cold pack lost
        # to sandbox setup. The action reads only its declared inputs and writes only its declared output, so the
        # sandbox was buying nothing.
        execution_requirements = {"no-sandbox": "1"},
    )]

native_content_module_packer = rule(
    doc = "A packer that is a single native executable.",
    implementation = _native_content_module_packer_impl,
    attrs = {
        "binary": attr.label(
            doc = "The packer executable.",
            mandatory = True,
            executable = True,
            cfg = "exec",
        ),
    },
)

def _unset_content_module_packer_impl(ctx):
    """The default the flag carries when nothing has been pointed at it.

    It fails when a jar is actually packed rather than during analysis, and that distinction is load-bearing: the
    packer attribute is an implicit dependency of *every* `jvm_library`, so failing at analysis would break a build
    that never packs anything - which is most builds in a workspace that only consumes these rules.
    """
    script = ctx.actions.declare_file(ctx.label.name + ".sh")
    ctx.actions.write(
        output = script,
        is_executable = True,
        content = """#!/bin/sh
echo "ERROR: no content-module packer is configured." >&2
echo "  Point --@rules_jvm//:content-module-packer at an implementation, e.g. in .bazelrc:" >&2
echo "    common --@rules_jvm//:content-module-packer=//build/content-module-packer:packer_tool" >&2
exit 1
""",
    )
    return [ContentModulePackerInfo(
        executable = script,
        argument_prefix = [],
        tools = depset([script]),
        execution_requirements = {},
    )]

unset_content_module_packer = rule(
    doc = "The flag's default: an implementation that explains how to configure one.",
    implementation = _unset_content_module_packer_impl,
)
