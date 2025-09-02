_KOPTS = {
    "warn": struct(
        args = dict(
            default = "",
            doc = "Control warning behaviour.",
            values = ["off", "error"],
        ),
        type = attr.string,
        flag_name = "warn",
    ),
    "context_receivers": struct(
        args = dict(
            default = False,
            doc = "Enable experimental context receivers.",
        ),
        type = attr.bool,
        flag_name = "context-receivers",
    ),
    "x_inline_classes": struct(
        args = dict(
            default = False,
            doc = "Enable experimental inline classes",
        ),
        type = attr.bool,
        flag_name = "inline-classes",
    ),
    "jvm_default": struct(
        args = dict(
            default = "",
            doc = "Specifies that a JVM default method should be generated for non-abstract Kotlin interface member.",
            values = ["all-compatibility", "all"],
        ),
        type = attr.string,
        flag_name = "jvm-default",
    ),
    "x_lambdas": struct(
        flag = "-Xlambdas",
        args = dict(
            default = "",
            doc = "Change codegen behavior of lambdas",
            values = ["class", "indy"],
        ),
        type = attr.string,
        flag_name = "lambdas",
    ),
    "opt_in": struct(
        args = dict(
            default = [],
            doc = "Define APIs to opt-in to.",
        ),
        type = attr.string_list,
        flag_name = "opt-in",
    ),
    "allow_kotlin_package": struct(
        args = dict(
            default = False,
            doc = "",
        ),
        type = attr.bool,
        flag_name = "allow-kotlin-package",
    ),
    "when_guards": struct(
        args = dict(
            default = False,
            doc = "",
        ),
        type = attr.bool,
        flag_name = "when-guards",
    ),
    "jvm_target": struct(
        args = dict(
            default = 0,
            doc = "",
        ),
        type = attr.int,
        flag_name = "jvm-target",
    ),
    "strict_kotlin_deps": struct(
        args = dict(
            default = "warn",
            doc = "",
        ),
        type = attr.string,
    ),
    "strict_java_deps": struct(
        args = dict(
            default = "warn",
            doc = "",
        ),
        type = attr.string,
    ),
    "report_unused_deps": struct(
        args = dict(
            default = "warn",
            doc = "",
            values = ["off", "warn", "error"],
        ),
        type = attr.string,
    ),
    "inc_threshold": struct(
        args = dict(
            default = -1,
            doc = "",
        ),
        type = attr.int,
    ),
}

# todo: experimental_strict_kotlin_deps, experimental_reduce_classpath_mode
KotlincOptions = provider(
    fields = {
        name: o.args["doc"]
        for name, o in _KOPTS.items()
    },
)

def _kotlinc_options_impl(ctx):
    return [KotlincOptions(**{n: getattr(ctx.attr, n, None) for n in _KOPTS})]

kt_kotlinc_options = rule(
    implementation = _kotlinc_options_impl,
    doc = "Define kotlin compiler options.",
    provides = [KotlincOptions],
    attrs = {n: o.type(**o.args) for n, o in _KOPTS.items()},
)

# Used by the Bazel plugin
def kotlinc_options_to_flags(kotlinc_options):
    flags = []
    for n, o in _KOPTS.items():
        value = getattr(kotlinc_options, n, None)
        if value:
            flagName = getattr(o, "flag_name", None)
            if flagName:
                if value == True:
                    flags.append("-" + flagName)
                elif type(value) == "list":
                    for element in value:
                        flags.append("-" + flagName + "=" + str(element))
                else:
                    flags.append("-" + flagName + "=" + str(value))
    return flags

def kotlinc_options_to_args(kotlinc_options, args):
    for n, o in _KOPTS.items():
        value = getattr(kotlinc_options, n, None)
        if value:
            flagName = getattr(o, "flag_name", None)
            if flagName:
                if value == True:
                    args.add("--" + flagName)
                elif type(value) == "list":
                    args.add_all("--" + flagName, value)
                else:
                    args.add("--" + flagName, value)
