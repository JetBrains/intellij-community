visibility("private")

def _jvm_platform_transition_impl(_settings, _attr):
    return {
        "//command_line_option:platforms": ["//rules/impl/platforms:jvm"],
    }

jvm_platform_transition = transition(
    implementation = _jvm_platform_transition_impl,
    inputs = [],
    outputs = ["//command_line_option:platforms"],
)

def _scrubbed_host_platform_transition_impl(_settings, _attr):
    return {
        "//command_line_option:platforms": ["//rules/impl/platforms:scrubbed_host"],
    }

scrubbed_host_platform_transition = transition(
    implementation = _scrubbed_host_platform_transition_impl,
    inputs = [],
    outputs = ["//command_line_option:platforms"],
)

def _scrubbed_host_tool_impl(ctx):
    return [DefaultInfo(files = depset([ctx.file.tool]))]

scrubbed_host_tool = rule(
    doc = "Exposes the compiler JAR as tool under the scrubbed host platform. Consumers must use the exec configuration.",
    implementation = _scrubbed_host_tool_impl,
    attrs = {
        "tool": attr.label(
            allow_single_file = True,
            cfg = scrubbed_host_platform_transition,
        ),
    },
)
