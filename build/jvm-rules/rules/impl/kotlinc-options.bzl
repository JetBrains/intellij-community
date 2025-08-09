_value_to_flag_info = provider(
    fields = {
        "ctx": "_derive_flag_ctx",
        "derive": "function(ctx, value) -> [] ",
    },
)

_derive_flag_ctx = provider(
    fields = {"name": "flag name for the compiler"},
)

def _derive_repeated_flag(ctx, value):
    return ["%s%s" % (ctx.name, v) for v in value]

def _repeated_values_for(name):
    return _value_to_flag_info(
        ctx = _derive_flag_ctx(name = name),
        derive = _derive_repeated_flag,
    )

derive = struct(
    info = _value_to_flag_info,
    repeated_values_for = _repeated_values_for,
)

def _map_jvm_target_to_flag(version):
    if not version:
        return None
    return ["-jvm-target=%s" % version]

def _map_api_version_to_flag(version):
    if not version:
        return None
    return ["-api-version=%s" % version]

def _map_language_version_to_flag(version):
    if not version:
        return None
    return ["-language-version=%s" % version]

def _map_opt_in_class_to_flag(values):
    return ["-opt-in=%s" % v for v in values]

def _map_plugins_optins_to_flag(values):
    result = []
    for v in values:
        result += ["-P", v]
    return result

_KOPTS = {
#     "jvm_default": struct(
#         flag = "-jvm-default",
#         args = dict(
#             default = "enable",
#             doc = "Emit JVM default methods for interface declarations with bodies.",
#             values = ["enable", "no-compatibility", "disable"],
#         ),
#         type = attr.string,
#         value_to_flag = {
#             "enable": ["-jvm-default=enable"],
#             "no-compatibility": ["-jvm-default=no-compatibility"],
#             "disable": ["-jvm-default=enable"],
#         },
#     ),
    "jvm_target": struct(
        args = dict(
            default = "",
            doc = "The target version of the generated JVM bytecode",
            values = ["6", "7", "8", "9", "10", "11", "12", "13", "15", "16", "17"],
        ),
        type = attr.string,
        value_to_flag = None,
        map_value_to_flag = _map_jvm_target_to_flag,
    ),
    "api_version": struct(
        args = dict(
            default = "",
            doc = "Allow using declarations only from the specified version of Kotlin bundled libraries",
            values = ["2.0", "2.1", "2.2"],
        ),
        type = attr.string,
        value_to_flag = None,
        map_value_to_flag = _map_api_version_to_flag,
    ),
    "language_version": struct(
        args = dict(
            default = "",
            doc = "Provide source compatibility with the specified version of Kotlin",
            values = ["2.0", "2.1", "2.2"],
        ),
        type = attr.string,
        value_to_flag = None,
        map_value_to_flag = _map_language_version_to_flag,
    ),
    "opt_in": struct(
        args = dict(
            default = [],
            doc = "Define APIs to opt-in to.",
        ),
        type = attr.string_list,
        value_to_flag = None,
        map_value_to_flag = _map_opt_in_class_to_flag,
    ),
    "plugin_options": struct(
        args = dict(
            default = [],
            doc = "Define complier plugin options.",
        ),
        type = attr.string_list,
        value_to_flag = None,
        map_value_to_flag = _map_plugins_optins_to_flag,
    ),
    "warn": struct(
        args = dict(
            default = "report",
            doc = "Control warning behaviour.",
            values = ["off", "report", "error"],
        ),
        type = attr.string,
        value_to_flag = {
            "off": ["-nowarn"],
            "report": None,
            "error": ["-Werror"],
        },
    ),
    "x_allow_kotlin_package": struct(
        args = dict(
            default = False,
            doc = "Allow compiling code in the 'kotlin' package, and allow not requiring 'kotlin.stdlib' in 'module-info'.",
        ),
        type = attr.bool,
        value_to_flag = {
            True: ["-Xallow-kotlin-package"],
        },
    ),
    "x_allow_result_return_type": struct(
        flag = "-Xallow-result-return-type",
        args = dict(
            default = False,
            doc = "Enable kotlin.Result as a return type",
        ),
        type = attr.bool,
        value_to_flag = {
            True: ["-Xallow-result-return-type"],
        },
    ),
    "x_allow_unstable_dependencies": struct(
        flag = "-Xallow-unstable-dependencies",
        args = dict(
            default = False,
            doc = "Do not report errors on classes in dependencies that were compiled by an unstable version of the Kotlin compiler.",
        ),
        type = attr.bool,
        value_to_flag = {
            True: ["-Xallow-unstable-dependencies"],
        },
    ),
    "x_consistent_data_class_copy_visibility": struct(
        args = dict(
            default = False,
            doc = "The effect of this compiler flag is the same as applying @ConsistentCopyVisibility annotation to all data classes in the module. See https://youtrack.jetbrains.com/issue/KT-11914",
        ),
        type = attr.bool,
        value_to_flag = {
            True: ["-Xconsistent-data-class-copy-visibility"],
        },
    ),
    "x_context_parameters": struct(
        flag = "-Xcontext-parameters",
        args = dict(
            default = False,
            doc = "Enable experimental context parameters.",
        ),
        type = attr.bool,
        value_to_flag = {
            True: ["-Xcontext-parameters"],
        },
    ),
    "x_context_receivers": struct(
        flag = "-Xcontext-receivers",
        args = dict(
            default = False,
            doc = "Enable experimental context receivers.",
        ),
        type = attr.bool,
        value_to_flag = {
            True: ["-Xcontext-receivers"],
        },
    ),
    "x_explicit_api_mode": struct(
        flag = "-Xexplicit-api",
        args = dict(
            default = "disable",
            doc = "Enable explicit API mode for Kotlin libraries.",
            values = ["disable", "warning", "strict"],
        ),
        type = attr.string,
        value_to_flag = {
            "off": None,
            "warning": ["-Xexplicit-api=warning"],
            "strict": ["-Xexplicit-api=strict"],
        },
    ),
    "x_inline_classes": struct(
        flag = "-Xinline-classes",
        args = dict(
            default = False,
            doc = "Enable experimental inline classes",
        ),
        type = attr.bool,
        value_to_flag = {
            True: ["-Xinline-classes"],
        },
    ),
    "x_jvm_default": struct(
        flag = "-Xjvm-default",
        args = dict(
            default = "off",
            doc = "Specifies that a JVM default method should be generated for non-abstract Kotlin interface member. Deprecated since compiler version 2.2",
            values = ["off", "enable", "disable", "compatibility", "all-compatibility", "all"],
        ),
        type = attr.string,
        value_to_flag = {
            "off": None,
            "enable": ["-Xjvm-default=enable"],
            "disable": ["-Xjvm-default=disable"],
            "compatibility": ["-Xjvm-default=compatibility"],
            "all-compatibility": ["-Xjvm-default=all-compatibility"],
            "all": ["-Xjvm-default=all"],
        },
    ),
    "x_lambdas": struct(
        flag = "-Xlambdas",
        args = dict(
            default = "class",
            doc = "Change codegen behavior of lambdas",
            values = ["class", "indy"],
        ),
        type = attr.string,
        value_to_flag = {
            "class": ["-Xlambdas=class"],
            "indy": ["-Xlambdas=indy"],
        },
    ),
    "x_no_call_assertions": struct(
        flag = "-Xno-call-assertions",
        args = dict(
            default = False,
            doc = "Don't generate not-null assertions for arguments of platform types",
        ),
        type = attr.bool,
        value_to_flag = {
            True: ["-Xno-call-assertions"],
        },
    ),
    "x_no_param_assertions": struct(
        flag = "-Xno-param-assertions",
        args = dict(
            default = False,
            doc = "Don't generate not-null assertions on parameters of methods accessible from Java",
        ),
        type = attr.bool,
        value_to_flag = {
            True: ["-Xno-param-assertions"],
        },
    ),
    "x_sam_conversions": struct(
        flag = "-Xsam-conversions",
        args = dict(
            default = "indy",
            doc = "Change codegen behavior of SAM/functional interfaces",
            values = ["class", "indy"],
        ),
        type = attr.string,
        value_to_flag = {
            "class": ["-Xsam-conversions=class"],
            "indy": ["-Xsam-conversions=indy"],
        },
    ),
    "x_skip_prerelease_check": struct(
        flag = "-Xskip-prerelease-check",
        args = dict(
            default = False,
            doc = "Suppress errors thrown when using pre-release classes.",
        ),
        type = attr.bool,
        value_to_flag = {
            True: ["-Xskip-prerelease-check"],
        },
    ),
    "x_strict_java_nullability_assertions": struct(
        flag = "-Xstrict-java-nullability-assertions",
        args = dict(
            default = False,
            doc = "Enable strict Java nullability assertions.",
        ),
        type = attr.bool,
        value_to_flag = {
            True: ["-Xstrict-java-nullability-assertions"],
        },
    ),
    "x_wasm_attach_js_exception": struct(
        flag = "-Xwasm-attach-js-exception",
        args = dict(
            default = False,
            doc = "Enable experimental support for attaching JavaScript exceptions to Kotlin exceptions.",
        ),
        type = attr.bool,
        value_to_flag = {
            True: ["-Xwasm-attach-js-exception"],
        },
    ),
    "x_when_guards": struct(
        flag = "-Xwhen-guards",
        args = dict(
            default = False,
            doc = "Enable experimental language support for when guards.",
        ),
        type = attr.bool,
        value_to_flag = {
            True: ["-Xwhen-guards"],
        },
    ),
    "x_x_language" :struct(
        args = dict(
            default = "",
            doc = "Language compatibility flags",
            values = ["", "+InlineClasses"],
        ),
        type = attr.string,
        value_to_flag = {
            "+InlineClasses": ["-XXLanguage:+InlineClasses"],
            "": None,
        },
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


def _to_flags(opts, attr_provider):
    """Translate options to flags

    Args:
        opts dict of name to struct
        attr options provider
    Returns:
        list of flags to add to the command line.
    """
    if not attr_provider:
        return ""

    flags = []
    for n, o in opts.items():
        value = getattr(attr_provider, n, None)
        if value == None:
            continue
        if o.value_to_flag and o.value_to_flag.get(derive.info, None):
            info = o.value_to_flag[derive.info]
            flag = info.derive(info.ctx, value)
        elif o.value_to_flag:
            flag = o.value_to_flag.get(value, None)
        else:
            flag = o.map_value_to_flag(value)
        if flag:
            flags.extend(flag)
    return flags


# Used by the Bazel plugin
def kotlinc_options_to_flags(kotlinc_options):
    """Translate KotlincOptions to worker flags for Bazel Plugin

    Args:
        kotlinc_options maybe containing KotlincOptions
    Returns:
        list of flags to add to the command line.
    """
    return _to_flags(_KOPTS, kotlinc_options)


def kotlinc_options_to_args(kotlinc_options, args):
    """Translate KotlincOptions to worker flags

    Args:
        kotlinc_options maybe containing KotlincOptions
    Returns:
        list of flags to add to the command line.
    """

    """
    flags = _to_flags(_KOPTS, kotlinc_options)
    for f in flags:
      args.add(f)
    """

    for n, o in _KOPTS.items():
        value = getattr(kotlinc_options, n, None)
        if value:
            flagName = n
            if value == True:
               args.add("--" + flagName)
            elif type(value) == "list":
                args.add_all("--" + flagName, value)
            else:
                args.add("--" + flagName, value)
