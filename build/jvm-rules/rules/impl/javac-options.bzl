load("//:rules/impl/kotlinc-options.bzl", "derive")

_JOPTS = {
    "warn": struct(
        args = dict(
            default = "report",
            doc = "Control warning behaviour.",
            values = ["off", "report", "error"],
        ),
        type = attr.string,
        value_to_flag = {
            "off": ["-nowarn"],
            "error": ["-Werror"],
            "report": None,
        },
    ),
    "release": struct(
        args = dict(
            default = "default",
            doc = "Compile for the specified Java SE release",
            values = ["default", "8", "11", "17", "21"],
        ),
        type = attr.string,
        value_to_flag = {
            "8": ["--release 8"],
            "11": ["--release 11"],
            "17": ["--release 17"],
            "21": ["--release 21"],
            "default": None,
        },
    ),
    "x_ep_disable_all_checks": struct(
        args = dict(
            default = False,
            doc = "See javac -XepDisableAllChecks documentation",
        ),
        type = attr.bool,
        value_to_flag = {
            True: ["-XepDisableAllChecks"],
        },
    ),
    "x_lint": struct(
        args = dict(
            default = [],
            doc = "See javac -Xlint: documentation",
        ),
        type = attr.string_list,
        value_to_flag = {
            derive.info: derive.repeated_values_for("-Xlint:"),
        },
    ),
    "xd_suppress_notes": struct(
        args = dict(
            default = False,
            doc = "See javac -XDsuppressNotes documentation",
        ),
        type = attr.bool,
        value_to_flag = {
            True: ["-XDsuppressNotes"],
        },
    ),
    "x_explicit_api_mode": struct(
        args = dict(
            default = "off",
            doc = "Enable explicit API mode for Kotlin libraries.",
            values = ["off", "warning", "strict"],
        ),
        type = attr.string,
        value_to_flag = {
            "off": None,
            "warning": ["-Xexplicit-api=warning"],
            "strict": ["-Xexplicit-api=strict"],
        },
    ),
    "add_exports": struct(
        args = dict(
            default = [],
            doc = "Export internal jdk apis",
        ),
        type = attr.string_list,
        value_to_flag = {
            derive.info: derive.repeated_values_for("--add-exports="),
        },
    ),

    # TODO: Allow to disable annotation processing (aka -proc:none) during Java compilation (https://github.com/bazelbuild/rules_kotlin/issues/1356)
    "no_proc": struct(
        args = dict(
            default = False,
            doc = "Disables annotation processing during Java compilation.",
        ),
        type = attr.bool,
        value_to_flag = {
            True: ["-proc:none"],
        },
    ),
}

JavacOptions = provider(
    fields = {
        name: o.args["doc"]
        for name, o in _JOPTS.items()
    },
)

def _javac_options_impl(ctx):
    return [JavacOptions(**{n: getattr(ctx.attr, n, None) for n in _JOPTS})]

kt_javac_options = rule(
    implementation = _javac_options_impl,
    doc = "Define java compiler options for `kt_jvm_*` rules with java sources.",
    provides = [JavacOptions],
    attrs = {n: o.type(**o.args) for n, o in _JOPTS.items()},
)
