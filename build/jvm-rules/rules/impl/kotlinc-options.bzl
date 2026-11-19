load(
    "@rules_kotlin//kotlin/internal:opts.bzl",
    _RKKotlincOptions = "KotlincOptions",
    _rk_kotlinc_options_to_flags = "kotlinc_options_to_flags",
    _rk_kt_kotlinc_options = "kt_kotlinc_options",
)

# The Kotlin compiler options of a target are the rules_kotlin provider; rules_jvm adds no JVM option of its own.
KotlincOptions = _RKKotlincOptions

# The options rules_jvm keeps next to KotlincOptions: the Kotlin/Wasm options the wasmjs rules read. The rules_kotlin
# provider does not model them.
KotlincExtraOptionsInfo = provider(
    doc = "Kotlin compiler options rules_jvm carries next to the rules_kotlin KotlincOptions provider.",
    fields = {
        "x_wasm_attach_js_exception": "Attach a JavaScript exception to a Wasm exception (-Xwasm-attach-js-exception).",
        "x_wasm_generate_closed_world_multimodule": "Generate closed-world multimodule Wasm (-Xwasm-generate-closed-world-multimodule).",
        "x_wasm_kclass_fqn": "Support KClass::qualifiedName in Wasm (-Xwasm-kclass-fqn).",
    },
)

_WASM_OPTION_FLAGS = {
    "x_wasm_attach_js_exception": "-Xwasm-attach-js-exception",
    "x_wasm_generate_closed_world_multimodule": "-Xwasm-generate-closed-world-multimodule",
    "x_wasm_kclass_fqn": "-Xwasm-kclass-fqn",
}

_EXTRA_OPTION_FIELDS = list(_WASM_OPTION_FLAGS.keys())

_EXTRA_OPTION_ATTRS = {
    name: attr.bool(
        default = False,
        doc = "Passes %s to the Kotlin/Wasm compiler." % flag,
    )
    for name, flag in _WASM_OPTION_FLAGS.items()
}

def _kt_kotlinc_options_with_extra_impl(ctx):
    return [
        ctx.attr.kotlinc_options[KotlincOptions],
        KotlincExtraOptionsInfo(**{name: getattr(ctx.attr, name) for name in _EXTRA_OPTION_FIELDS}),
    ]

_kt_kotlinc_options_with_extra = rule(
    implementation = _kt_kotlinc_options_with_extra_impl,
    attrs = dict(
        _EXTRA_OPTION_ATTRS,
        kotlinc_options = attr.label(
            mandatory = True,
            providers = [KotlincOptions],
        ),
        # Every jvm_library names a kotlinc options target on both backends, so a dependency here makes every
        # build analyze the guard of the protobuf toolchains (//:proto_toolchain_guard): a lost toolchain
        # registration or a protoc built from source fails the build instead of passing silently.
        _proto_toolchain_guard = attr.label(
            default = Label("//:proto_toolchain_guard"),
        ),
    ),
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

def kt_kotlinc_options(name, **kwargs):
    """Defines Kotlin compiler options: rules_kotlin's kt_kotlinc_options plus the rules_jvm extra options.

    Every option except the extra options goes to rules_kotlin's `kt_kotlinc_options` under the rules_kotlin
    attribute name. The target provides `KotlincOptions` and `KotlincExtraOptionsInfo`.

    Args:
        name: The target name.
        **kwargs: The options and the common rule attributes.
    """
    rule_attrs = {attr_name: kwargs.pop(attr_name) for attr_name in _RULE_ATTR_NAMES if attr_name in kwargs}
    extra_kwargs = {option: kwargs.pop(option) for option in _EXTRA_OPTION_FIELDS if option in kwargs}

    rk_options_name = name + "_rules_kotlin_options"
    _rk_kt_kotlinc_options(
        name = rk_options_name,
        visibility = ["//visibility:private"],
        **kwargs
    )
    _kt_kotlinc_options_with_extra(
        name = name,
        kotlinc_options = ":" + rk_options_name,
        **dict(extra_kwargs, **rule_attrs)
    )

def _extra_options_to_flags(kotlinc_extra_options):
    if not kotlinc_extra_options:
        return []
    return [
        flag
        for name, flag in _WASM_OPTION_FLAGS.items()
        if getattr(kotlinc_extra_options, name, False)
    ]

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

# The options the JPS worker takes, in worker flag order. Each is the name of a KotlincOptions field, and the
# worker flag is that name with a `--` prefix.
_WORKER_OPTION_NAMES = [
    "jvm_target",
    "api_version",
    "language_version",
    "x_optin",
    "warn",
    "x_allow_kotlin_package",
    "x_allow_unstable_dependencies",
    "x_compiler_plugin_order",
    "x_consistent_data_class_copy_visibility",
    "x_context_parameters",
    "x_context_receivers",
    "x_explicit_api_mode",
    "x_inline_classes",
    "x_jsr_305",
    "jvm_default",
    "x_lambdas",
    "x_no_call_assertions",
    "x_no_param_assertions",
    "progressive",
    "x_render_internal_diagnostic_names",
    "x_report_all_warnings",
    "x_sam_conversions",
    "x_skip_prerelease_check",
    "x_warning_level",
    "x_when_guards",
    "x_xlanguage",
]

def _kotlinc_worker_option_value(name, kotlinc_options):
    if not hasattr(kotlinc_options, name):
        return None
    value = getattr(kotlinc_options, name)
    if name == "x_explicit_api_mode" and value == "off":
        # "off" is the no-enforcement default of the attribute and of the worker: emit nothing.
        return None
    if name == "jvm_default" and value == "off":
        # "off" renders no flag in rules_kotlin, and the stable -jvm-default flag rejects it as a mode: emit nothing.
        return None
    if name == "x_jsr_305" and value == "default":
        # "default" renders no flag in rules_kotlin: emit nothing.
        return None
    if name == "x_warning_level":
        # rules_kotlin holds the option as a dict; the worker takes repeated `NAME:level` values.
        return ["%s:%s" % (diagnostic, level) for diagnostic, level in value.items()]
    return value

def kotlinc_options_to_args(kotlinc_options, args):
    """Translate Kotlinc options to worker flags.

    Args:
        kotlinc_options: rules_kotlin KotlincOptions.
        args: The Args object of the worker action.
    """
    for name in _WORKER_OPTION_NAMES:
        value = _kotlinc_worker_option_value(name, kotlinc_options)
        if value == None or value == "" or value == [] or value == False:
            continue

        # JPS worker uses jvm_target for both Kotlin and Java.
        # Kotlin accepts "1.8" but javac --release needs "8".
        if name == "jvm_target" and value == "1.8":
            value = "8"

        flag = "--" + name
        if value == True:
            args.add(flag)
        elif type(value) == "list":
            args.add_all(flag, value)
        else:
            args.add(flag, value)
