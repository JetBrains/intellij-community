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
    #     "include_stdlibs": struct(
    #         args = dict(
    #             default = "all",
    #             doc = "Don't automatically include the Kotlin standard libraries into the classpath (stdlib and reflect).",
    #             values = ["all", "stdlib", "none"],
    #         ),
    #         type = attr.string,
    #         value_to_flag = {
    #             "all": None,
    #             "stdlib": ["-no-reflect"],
    #             "none": ["-no-stdlib"],
    #         },
    #     ),
    #     "x_skip_prerelease_check": struct(
    #         flag = "-Xskip-prerelease-check",
    #         args = dict(
    #             default = False,
    #             doc = "Suppress errors thrown when using pre-release classes.",
    #         ),
    #         type = attr.bool,
    #         value_to_flag = {
    #             True: ["-Xskip-prerelease-check"],
    #         },
    #     ),
    "context_receivers": struct(
        args = dict(
            default = False,
            doc = "Enable experimental context receivers.",
        ),
        type = attr.bool,
        flag_name = "context-receivers",
    ),
    #     "x_suppress_version_warnings": struct(
    #         flag = "-Xsuppress-version-warnings",
    #         args = dict(
    #             default = False,
    #             doc = "Suppress warnings about outdated, inconsistent, or experimental language or API versions.",
    #         ),
    #         type = attr.bool,
    #         value_to_flag = {
    #             True: ["-Xsuppress-version-warnings"],
    #         },
    #     ),
    "x_inline_classes": struct(
        args = dict(
            default = False,
            doc = "Enable experimental inline classes",
        ),
        type = attr.bool,
        flag_name = "inline-classes",
    ),
    #     "x_allow_result_return_type": struct(
    #         flag = "-Xallow-result-return-type",
    #         args = dict(
    #             default = False,
    #             doc = "Enable kotlin.Result as a return type",
    #         ),
    #         type = attr.bool,
    #         value_to_flag = {
    #             True: ["-Xallow-result-return-type"],
    #         },
    #     ),
    "jvm_default": struct(
        args = dict(
            default = "",
            doc = "Specifies that a JVM default method should be generated for non-abstract Kotlin interface member.",
            values = ["all-compatibility", "all"],
        ),
        type = attr.string,
        flag_name = "jvm-default",
    ),
    #     "x_no_call_assertions": struct(
    #         flag = "-Xno-call-assertions",
    #         args = dict(
    #             default = False,
    #             doc = "Don't generate not-null assertions for arguments of platform types",
    #         ),
    #         type = attr.bool,
    #         value_to_flag = {
    #             True: ["-Xno-call-assertions"],
    #         },
    #     ),
    #     "x_no_param_assertions": struct(
    #         flag = "-Xno-param-assertions",
    #         args = dict(
    #             default = False,
    #             doc = "Don't generate not-null assertions on parameters of methods accessible from Java",
    #         ),
    #         type = attr.bool,
    #         value_to_flag = {
    #             True: ["-Xno-param-assertions"],
    #         },
    #     ),
    #     "x_no_receiver_assertions": struct(
    #         flag = "-Xno-receiver-assertions",
    #         args = dict(
    #             default = False,
    #             doc = "Don't generate not-null assertion for extension receiver arguments of platform types",
    #         ),
    #         type = attr.bool,
    #         value_to_flag = {
    #             True: ["-Xno-receiver-assertions"],
    #         },
    #     ),
    #     "x_no_optimized_callable_references": struct(
    #         flag = "-Xno-optimized-callable-references",
    #         args = dict(
    #             default = False,
    #             doc = "Do not use optimized callable reference superclasses. Available from 1.4.",
    #         ),
    #         type = attr.bool,
    #         value_to_flag = {
    #             True: ["-Xno-optimized-callable-references"],
    #         },
    #     ),
    #     "x_explicit_api_mode": struct(
    #         flag = "-Xexplicit-api",
    #         args = dict(
    #             default = "off",
    #             doc = "Enable explicit API mode for Kotlin libraries.",
    #             values = ["off", "warning", "strict"],
    #         ),
    #         type = attr.string,
    #         value_to_flag = {
    #             "off": None,
    #             "warning": ["-Xexplicit-api=warning"],
    #             "strict": ["-Xexplicit-api=strict"],
    #         },
    #     ),
    #     "java_parameters": struct(
    #         args = dict(
    #             default = False,
    #             doc = "Generate metadata for Java 1.8+ reflection on method parameters.",
    #         ),
    #         type = attr.bool,
    #         value_to_flag = {
    #             True: ["-java-parameters"],
    #         },
    #     ),
    #     "x_multi_platform": struct(
    #         flag = "-Xmulti-platform",
    #         args = dict(
    #             default = False,
    #             doc = "Enable experimental language support for multi-platform projects",
    #         ),
    #         type = attr.bool,
    #         value_to_flag = {
    #             True: ["-Xmulti-platform"],
    #         },
    #     ),
    #     "x_sam_conversions": struct(
    #         flag = "-Xsam-conversions",
    #         args = dict(
    #             default = "class",
    #             doc = "Change codegen behavior of SAM/functional interfaces",
    #             values = ["class", "indy"],
    #         ),
    #         type = attr.string,
    #         value_to_flag = {
    #             "class": ["-Xsam-conversions=class"],
    #             "indy": ["-Xsam-conversions=indy"],
    #         },
    #     ),
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
    #     "x_emit_jvm_type_annotations": struct(
    #         flag = "-Xemit-jvm-type-annotations",
    #         args = dict(
    #             default = False,
    #             doc = "Basic support for type annotations in JVM bytecode.",
    #         ),
    #         type = attr.bool,
    #         value_to_flag = {
    #             True: ["-Xemit-jvm-type-annotations"],
    #         },
    #     ),
    "opt_in": struct(
        args = dict(
            default = [],
            doc = "Define APIs to opt-in to.",
        ),
        type = attr.string_list,
    ),
    #     "x_use_fir": struct(
    #         # 1.6
    #         flag = "-Xuse-fir",
    #         args = dict(
    #             default = False,
    #             doc = "Compile using the experimental Kotlin Front-end IR. Available from 1.6.",
    #         ),
    #         type = attr.bool,
    #         value_to_flag = {
    #             True: ["-Xuse-fir"],
    #         },
    #     ),
    #     "x_use_k2": struct(
    #         # 1.7
    #         flag = "-Xuse-k2",
    #         args = dict(
    #             default = False,
    #             doc = "Compile using experimental K2. K2 is a new compiler pipeline, no compatibility guarantees are yet provided",
    #         ),
    #         type = attr.bool,
    #         value_to_flag = {
    #             True: ["-Xuse-k2"],
    #         },
    #     ),
    #     "x_no_optimize": struct(
    #         flag = "-Xno-optimize",
    #         args = dict(
    #             default = False,
    #             doc = "Disable optimizations",
    #         ),
    #         type = attr.bool,
    #         value_to_flag = {
    #             True: ["-Xno-optimize"],
    #         },
    #     ),
    #     "x_backend_threads": struct(
    #         # 1.6.20, 1.7
    #         flag = "-Xbackend-threads",
    #         args = dict(
    #             default = 1,
    #             doc = "When using the IR backend, run lowerings by file in N parallel threads. 0 means use a thread per processor core. Default value is 1.",
    #         ),
    #         type = attr.int,
    #         value_to_flag = None,
    #         map_value_to_flag = _map_backend_threads_to_flag,
    #     ),
    #     "x_enable_incremental_compilation": struct(
    #         args = dict(
    #             default = False,
    #             doc = "Enable incremental compilation",
    #         ),
    #         type = attr.bool,
    #         value_to_flag = {
    #             True: ["-Xenable-incremental-compilation"],
    #         },
    #     ),
    #     "x_report_perf": struct(
    #         flag = "-Xreport-perf",
    #         args = dict(
    #             default = False,
    #             doc = "Report detailed performance statistics",
    #         ),
    #         type = attr.bool,
    #         value_to_flag = {
    #             True: ["-Xreport-perf"],
    #         },
    #     ),
    #     "x_use_fir_lt": struct(
    #         args = dict(
    #             default = False,
    #             doc = "Compile using LightTree parser with Front-end IR. Warning: this feature is far from being production-ready",
    #         ),
    #         type = attr.bool,
    #         value_to_flag = {
    #             True: ["-Xuse-fir-lt"],
    #         },
    #     ),
    "allow_kotlin_package": struct(
        args = dict(
            default = False,
            doc = "",
        ),
        type = attr.bool,
        flag_name = "allow-kotlin-package",
    ),
    #     "x_no_source_debug_extension": struct(
    #         args = dict(
    #             default = False,
    #             doc = "Do not generate @kotlin.jvm.internal.SourceDebugExtension annotation on a class with the copy of SMAP",
    #         ),
    #         type = attr.bool,
    #         value_to_flag = {
    #             True: ["-Xno-source-debug-extension"],
    #         },
    #     ),
    #     "x_type_enhancement_improvements_strict_mode": struct(
    #         args = dict(
    #             default = False,
    #             doc = "Enables strict mode for type enhancement improvements, enforcing stricter type checking and enhancements.",
    #         ),
    #         type = attr.bool,
    #         value_to_flag = {
    #             True: ["-Xtype-enhancement-improvements-strict-mode"],
    #         },
    #     ),
    #     "x_jsr_305": struct(
    #         args = dict(
    #             default = "",
    #             doc = "Specifies how to handle JSR-305 annotations in Kotlin code. Options are 'default', 'ignore', 'warn', and 'strict'.",
    #             values = ["ignore", "warn", "strict"],
    #         ),
    #         type = attr.string,
    #         flag_name = "jsr305",
    #     ),
    #     "x_assertions": struct(
    #         args = dict(
    #             default = None,
    #             doc = "Configures how assertions are handled. The 'jvm' option enables assertions in JVM code.",
    #             values = ["jvm"],
    #         ),
    #         type = attr.string,
    #         flag_name = "assertions",
    #     ),
    #     "x_jspecify_annotations": struct(
    #         args = dict(
    #             default = None,
    #             doc = "Controls how JSpecify annotations are treated. Options are 'default', 'ignore', 'warn', and 'strict'.",
    #             values = ["ignore", "warn", "strict"],
    #         ),
    #         type = attr.string,
    #         add_arg = attr.string,
    #         flag_name = "jspecify-annotations",
    #     ),
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
    "jvm_emit_jdeps": struct(
        args = dict(
            default = True,
            doc = "",
        ),
        type = attr.bool,
    ),
    #     "x_jdk_release": struct(
    #         args = dict(
    #             default = "",
    #             doc = "The -jvm_target flag. This is only tested at 1.8.",
    #             values = ["1.6", "1.8", "9", "10", "11", "12", "13", "15", "16", "17"],
    #         ),
    #         type = attr.string,
    #         flag_name = "jdk_release",
    #     ),
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

def kotlinc_options_to_flags(kotlinc_options, args):
    for n, o in _KOPTS.items():
        value = getattr(kotlinc_options, n, None)
        if value:
            flagName = getattr(o, "flag_name", None)
            if flagName:
                if value == True:
                    args.add("--" + flagName)
                else:
                    args.add("--" + flagName, value)
