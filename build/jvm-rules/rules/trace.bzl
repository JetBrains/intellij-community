TraceInfo = provider(doc = "", fields = ["trace"])

def _impl(ctx):
    trace = ctx.build_setting_value
    return TraceInfo(trace = trace)

trace = rule(
    implementation = _impl,
    build_setting = config.bool(flag = True),
)
