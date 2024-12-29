load("//:rules/trace.bzl", "TraceInfo")

visibility("private")

def init_builder_args(ctx, rule_kind, module_name):
    """Initialize an arg object for a task that will be executed by the Kotlin Builder."""
    args = ctx.actions.args()
    args.set_param_file_format("multiline")
    args.use_param_file("--flagfile=%s", use_always = True)

    args.add("--target_label", ctx.label)
    args.add("--rule_kind", rule_kind)
    args.add("--kotlin_module_name", module_name)

    trace = ctx.attr._trace[TraceInfo].trace

    if ctx.attr._trace[TraceInfo].trace:
        args.add("--trace")

    return args
