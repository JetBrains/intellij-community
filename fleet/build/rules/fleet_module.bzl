visibility("private")

def _fleet_module(ctx):
    return [
        DefaultInfo(
            files = depset([]),
        ),
    ]

fleet_module = rule(
    attrs = {
        "module_name": attr.string(
            doc = """The Fleet module name""",
        ),
        "module_library": attr.label(
            providers = [
                [JavaInfo],
            ],
            allow_files = False,
        ),
        "_haven_cli": attr.label(
            default = "//fleet/build/cli:haven",
            executable = True,
            #             allow_single_file = True,
            cfg = "exec",
            # cfg = scrubbed_host_platform_transition,
        ),
    },
    provides = [],
    implementation = _fleet_module,
)
