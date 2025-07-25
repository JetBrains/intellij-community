load("@rules_kotlin//kotlin:jvm.bzl", "kt_javac_options")
load("@rules_jvm//:jvm.bzl", "kt_kotlinc_options")

# We set default options for IntelliJ project
def create_kotlinc_options(name, jvm_target = "17", opt_in = [], plugin_options = [], warn = "off", x_allow_kotlin_package = False, x_allow_result_return_type = False, x_allow_unstable_dependencies = False, x_consistent_data_class_copy_visibility = False, x_context_parameters = False, x_context_receivers = False,  x_explicit_api_mode = "disable", x_inline_classes = False, x_jvm_default = "all", x_lambdas = "indy", x_no_call_assertions = False, x_no_param_assertions = False, x_sam_conversions = "indy", x_skip_prerelease_check = False, x_strict_java_nullability_assertions = False, x_wasm_attach_js_exception = False, x_when_guards = False, x_x_language = ""):
  kt_kotlinc_options(
    name = name,
    jvm_target = jvm_target,
    opt_in = [
      "com.intellij.openapi.util.IntellijInternalApi",
    ] + opt_in,
    plugin_options = plugin_options,
    visibility=["//visibility:public"],
    warn = warn,
    x_allow_kotlin_package = x_allow_kotlin_package,
    x_allow_result_return_type = x_allow_result_return_type,
    x_allow_unstable_dependencies = x_allow_unstable_dependencies,
    x_consistent_data_class_copy_visibility = x_consistent_data_class_copy_visibility,
    x_context_parameters = x_context_parameters,
    x_context_receivers = x_context_receivers,
    x_explicit_api_mode = x_explicit_api_mode,
    x_inline_classes = x_inline_classes,
    x_jvm_default = x_jvm_default,
    x_lambdas = x_lambdas,
    x_no_call_assertions = x_no_call_assertions,
    x_no_param_assertions = x_no_param_assertions,
    x_sam_conversions = x_sam_conversions,
    x_skip_prerelease_check = x_skip_prerelease_check,
    x_strict_java_nullability_assertions = x_strict_java_nullability_assertions,
    x_wasm_attach_js_exception = x_wasm_attach_js_exception,
    x_when_guards = x_when_guards,
    x_x_language = x_x_language,
  )
