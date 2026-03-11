load("@rules_jvm//:jvm.bzl", "kt_kotlinc_options")

# Keep callsites on "8" while normalizing to rules_kotlin-compatible "1.8".
def _normalize_kotlinc_jvm_target(jvm_target):
    return "1.8" if jvm_target == "8" else jvm_target

def _normalize_legacy_jvm_default(x_jvm_default):
    if x_jvm_default == "disable":
        return "disable"
    if x_jvm_default == "all-compatibility":
        return "enable"
    if x_jvm_default == "all":
        return "no-compatibility"
    fail("Unsupported x_jvm_default value: %s" % x_jvm_default)

# We set default options for IntelliJ project.
def create_kotlinc_options(
        name,
        jvm_target = "21",
        api_version = "2.3",
        language_version = "2.3",
        opt_in = ["com.intellij.openapi.util.IntellijInternalApi"],
        plugin_options = [],
        progressive = True,
        warn = "off",
        x_allow_kotlin_package = False,
        x_allow_result_return_type = False,
        x_allow_unstable_dependencies = False,
        x_consistent_data_class_copy_visibility = False,
        x_context_parameters = False,
        x_context_receivers = False,
        x_explicit_api_mode = "disable",
        x_inline_classes = False,
        x_jvm_default = None,
        jvm_default = "no-compatibility",
        x_lambdas = "indy",
        x_no_call_assertions = False,
        x_no_param_assertions = False,
        x_render_internal_diagnostic_names = False,
        x_report_all_warnings = False,
        x_sam_conversions = "indy",
        x_skip_prerelease_check = False,
        x_strict_java_nullability_assertions = False,
        x_wasm_attach_js_exception = False,
        x_wasm_kclass_fqn = False,
        x_when_guards = False,
        x_x_language = ["+AllowEagerSupertypeAccessibilityChecks"]):
    if x_jvm_default != None:
        jvm_default = _normalize_legacy_jvm_default(x_jvm_default)

    kt_kotlinc_options(
        name = name,
        jvm_target = _normalize_kotlinc_jvm_target(jvm_target),
        api_version = api_version,
        language_version = language_version,
        opt_in = opt_in,
        plugin_options = plugin_options,
        progressive = progressive,
        visibility = ["//visibility:public"],
        warn = warn,
        x_allow_kotlin_package = x_allow_kotlin_package,
        x_allow_result_return_type = x_allow_result_return_type,
        x_allow_unstable_dependencies = x_allow_unstable_dependencies,
        x_consistent_data_class_copy_visibility = x_consistent_data_class_copy_visibility,
        x_context_parameters = x_context_parameters,
        x_context_receivers = x_context_receivers,
        x_explicit_api = x_explicit_api_mode,
        x_inline_classes = x_inline_classes,
        jvm_default = jvm_default,
        x_lambdas = x_lambdas,
        x_no_call_assertions = x_no_call_assertions,
        x_no_param_assertions = x_no_param_assertions,
        x_render_internal_diagnostic_names = x_render_internal_diagnostic_names,
        x_report_all_warnings = x_report_all_warnings,
        x_sam_conversions = x_sam_conversions,
        x_skip_prerelease_check = x_skip_prerelease_check,
        x_strict_java_nullability_assertions = x_strict_java_nullability_assertions,
        x_wasm_attach_js_exception = x_wasm_attach_js_exception,
        x_wasm_kclass_fqn = x_wasm_kclass_fqn,
        x_when_guards = x_when_guards,
        x_xlanguage = x_x_language,
    )
