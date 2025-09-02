load("//:jvm.bzl", "kt_kotlinc_options")

def create_strange_kotlinc_options(name, jvm_target = 17, opt_in = [], allow_kotlin_package = False, context_receivers = False, when_guards = False):
    kt_kotlinc_options(
        name = name,
        allow_kotlin_package = allow_kotlin_package,
        context_receivers = context_receivers,
        jvm_default = "all",
        jvm_target = jvm_target,
        opt_in = [
            "com.intellij.openapi.util.IntellijInternalApi",
        ] + opt_in,
        visibility = ["//visibility:public"],
        warn = "off",
        when_guards = when_guards,
    )
