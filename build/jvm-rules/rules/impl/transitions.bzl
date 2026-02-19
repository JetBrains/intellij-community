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
