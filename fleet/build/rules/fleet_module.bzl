load("//fleet/build/rules:haven_cli.bzl", "HAVEN_CLI_ATTR", "run_haven_cli")

visibility("private")

def _fleet_module(ctx):
    return [
        DefaultInfo(
            files = depset([]),
        ),
    ]

fleet_module = rule(
    attrs = HAVEN_CLI_ATTR | {
        "module_name": attr.string(
            doc = """The Fleet module name""",
        ),
        "module_library": attr.label(
            providers = [
                [JavaInfo],
            ],
            allow_files = False,
        ),
    },
    provides = [],
    implementation = _fleet_module,
)
