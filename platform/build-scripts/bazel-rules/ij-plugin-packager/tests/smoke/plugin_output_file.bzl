"""Gives one file of the output of an `ij_plugin` target its own label, so that a test can name it in `$(rlocationpath)`."""

def _plugin_output_file_impl(ctx):
    files = [file for file in ctx.attr.plugin[DefaultInfo].files.to_list() if file.basename == ctx.attr.basename]
    if len(files) != 1:
        fail("Expected exactly one file named '%s' in the output of %s, got %s" % (ctx.attr.basename, ctx.attr.plugin.label, files))
    return [DefaultInfo(files = depset(files), runfiles = ctx.runfiles(files))]

plugin_output_file = rule(
    implementation = _plugin_output_file_impl,
    attrs = {
        "basename": attr.string(mandatory = True),
        "plugin": attr.label(mandatory = True),
    },
)
