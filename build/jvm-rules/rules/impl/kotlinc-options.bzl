load(
    "@rules_kotlin//kotlin/internal:opts.bzl",
    _RKKotlincOptions = "KotlincOptions",
    _rk_kotlinc_options_to_flags = "kotlinc_options_to_flags",
    _rk_kt_kotlinc_options = "kt_kotlinc_options",
)

KotlincOptions = _RKKotlincOptions

KotlincExtraOptionsInfo = provider(
    fields = {
        "api_version": "Kotlin API version used by the JPS backend.",
        "language_version": "Kotlin language version used by the JPS backend.",
        "plugin_options": "Additional -P compiler options for the JPS backend.",
        "x_allow_result_return_type": "Enable kotlin.Result as a return type for the JPS backend.",
        "x_strict_java_nullability_assertions": "Enable strict Java nullability assertions for the JPS backend.",
        "x_wasm_attach_js_exception": "Enable attaching JS exceptions for Wasm in the JPS backend.",
        "x_wasm_kclass_fqn": "Enable KClass::qualifiedName support for Wasm in the JPS backend.",
    },
)

_EXTRA_OPTION_FIELDS = [
    "api_version",
    "language_version",
    "plugin_options",
    "x_allow_result_return_type",
    "x_strict_java_nullability_assertions",
    "x_wasm_attach_js_exception",
    "x_wasm_kclass_fqn",
]

_EXTRA_OPTION_ATTRS = {
    "api_version": attr.string(
        default = "",
        doc = "Allow using declarations only from the specified version of Kotlin bundled libraries.",
    ),
    "language_version": attr.string(
        default = "",
        doc = "Provide source compatibility with the specified version of Kotlin.",
    ),
    "plugin_options": attr.string_list(
        default = [],
        doc = "Define compiler plugin options to pass as repeated -P entries.",
    ),
    "x_allow_result_return_type": attr.bool(
        default = False,
        doc = "Enable kotlin.Result as a return type.",
    ),
    "x_strict_java_nullability_assertions": attr.bool(
        default = False,
        doc = "Enable strict Java nullability assertions.",
    ),
    "x_wasm_attach_js_exception": attr.bool(
        default = False,
        doc = "Enable attaching JavaScript exceptions for Wasm.",
    ),
    "x_wasm_kclass_fqn": attr.bool(
        default = False,
        doc = "Enable KClass::qualifiedName support for Wasm.",
    ),
}

def _kt_kotlinc_options_with_extra_impl(ctx):
    extra_values = {name: getattr(ctx.attr, name, None) for name in _EXTRA_OPTION_FIELDS}
    return [
        ctx.attr.kotlinc_options[KotlincOptions],
        KotlincExtraOptionsInfo(**extra_values),
    ]

_KT_KOTLINC_OPTIONS_WITH_EXTRA_ATTRS = dict(_EXTRA_OPTION_ATTRS)
_KT_KOTLINC_OPTIONS_WITH_EXTRA_ATTRS.update({
    "kotlinc_options": attr.label(
        mandatory = True,
        providers = [KotlincOptions],
    ),
})

_kt_kotlinc_options_with_extra = rule(
    implementation = _kt_kotlinc_options_with_extra_impl,
    attrs = _KT_KOTLINC_OPTIONS_WITH_EXTRA_ATTRS,
    provides = [KotlincOptions, KotlincExtraOptionsInfo],
)

_RULE_ATTR_NAMES = [
    "compatible_with",
    "deprecation",
    "features",
    "restricted_to",
    "tags",
    "target_compatible_with",
    "testonly",
    "visibility",
]

def _extract_rule_attrs(kwargs):
    attrs = {}
    for name in _RULE_ATTR_NAMES:
        if name in kwargs:
            attrs[name] = kwargs.pop(name)
    return attrs

def _merge_dicts(left, right):
    result = {}
    result.update(left)
    result.update(right)
    return result

def kt_kotlinc_options(name, **kwargs):
    """Define Kotlin compiler options with standard rules_kotlin providers.

    Standard options are delegated to rules_kotlin's kt_kotlinc_options.
    rules_jvm-specific options are carried via KotlincExtraOptionsInfo.
    """
    rule_attrs = _extract_rule_attrs(kwargs)

    rk_kwargs = {}
    extra_kwargs = {}
    for option_name, value in kwargs.items():
        if option_name in _EXTRA_OPTION_FIELDS:
            extra_kwargs[option_name] = value
        else:
            rk_kwargs[option_name] = value

    rk_options_name = name + "_rules_kotlin_options"

    _rk_kt_kotlinc_options(
        name = rk_options_name,
        visibility = ["//visibility:private"],
        **rk_kwargs
    )

    _kt_kotlinc_options_with_extra(
        name = name,
        kotlinc_options = ":" + rk_options_name,
        **_merge_dicts(extra_kwargs, rule_attrs)
    )

def _extra_options_to_flags(kotlinc_extra_options):
    if not kotlinc_extra_options:
        return []

    flags = []

    api_version = getattr(kotlinc_extra_options, "api_version", None)
    if api_version:
        flags.append("-api-version=%s" % api_version)

    language_version = getattr(kotlinc_extra_options, "language_version", None)
    if language_version:
        flags.append("-language-version=%s" % language_version)

    plugin_options = getattr(kotlinc_extra_options, "plugin_options", None)
    if plugin_options:
        for option in plugin_options:
            flags.extend(["-P", option])

    if getattr(kotlinc_extra_options, "x_allow_result_return_type", False):
        flags.append("-Xallow-result-return-type")
    if getattr(kotlinc_extra_options, "x_strict_java_nullability_assertions", False):
        flags.append("-Xstrict-java-nullability-assertions")
    if getattr(kotlinc_extra_options, "x_wasm_attach_js_exception", False):
        flags.append("-Xwasm-attach-js-exception")
    if getattr(kotlinc_extra_options, "x_wasm_kclass_fqn", False):
        flags.append("-Xwasm-kclass-fqn")

    return flags

# Used by the Bazel plugin.
def kotlinc_options_to_flags(kotlinc_options, kotlinc_extra_options = None):
    """Translate Kotlinc options to compiler flags.

    Args:
        kotlinc_options: rules_kotlin KotlincOptions.
        kotlinc_extra_options: Optional KotlincExtraOptionsInfo.
    Returns:
        list of flags to add to the command line.
    """
    flags = _rk_kotlinc_options_to_flags(kotlinc_options) if kotlinc_options else []
    flags.extend(_extra_options_to_flags(kotlinc_extra_options))
    return flags

_WORKER_OPTION_NAMES = [
    "jvm_target",
    "api_version",
    "language_version",
    "opt_in",
    "plugin_options",
    "warn",
    "x_allow_kotlin_package",
    "x_allow_result_return_type",
    "x_allow_unstable_dependencies",
    "x_consistent_data_class_copy_visibility",
    "x_context_parameters",
    "x_context_receivers",
    "x_explicit_api",
    "x_inline_classes",
    "jvm_default",
    "x_lambdas",
    "x_no_call_assertions",
    "x_no_param_assertions",
    "progressive",
    "x_render_internal_diagnostic_names",
    "x_report_all_warnings",
    "x_sam_conversions",
    "x_skip_prerelease_check",
    "x_strict_java_nullability_assertions",
    "x_wasm_attach_js_exception",
    "x_wasm_kclass_fqn",
    "x_when_guards",
    "x_xlanguage",
]

def _kotlinc_worker_option_value(name, kotlinc_options, kotlinc_extra_options):
    if kotlinc_extra_options and name in _EXTRA_OPTION_FIELDS:
        return getattr(kotlinc_extra_options, name, None)

    if not kotlinc_options:
        return None

    if not hasattr(kotlinc_options, name):
        return None
    return getattr(kotlinc_options, name, None)

def kotlinc_options_to_args(kotlinc_options, args, kotlinc_extra_options = None):
    """Translate Kotlinc options to worker flags.

    Args:
        kotlinc_options: rules_kotlin KotlincOptions.
        kotlinc_extra_options: Optional KotlincExtraOptionsInfo.
    """
    for name in _WORKER_OPTION_NAMES:
        value = _kotlinc_worker_option_value(name, kotlinc_options, kotlinc_extra_options)
        if value == None or value == "" or value == [] or value == False:
            continue

        # JPS worker uses jvm_target for both Kotlin and Java.
        # Kotlin accepts "1.8" but javac --release needs "8".
        if name == "jvm_target" and value == "1.8":
            value = "8"

        if value == True:
            args.add("--" + name)
        elif type(value) == "list":
            args.add_all("--" + name, value)
        else:
            args.add("--" + name, value)
