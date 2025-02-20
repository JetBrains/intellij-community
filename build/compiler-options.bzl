load("@rules_kotlin//kotlin:jvm.bzl", "kt_javac_options")
load("@rules_jvm//:jvm.bzl", "kt_kotlinc_options")

def create_kotlinc_options(name, jvm_target = 17, opt_in = [], allow_kotlin_package = False, context_receivers = False, when_guards = False):
  kt_kotlinc_options(
    name = name,
    jvm_target = jvm_target,
    opt_in = [
      "com.intellij.openapi.util.IntellijInternalApi",
    ] + opt_in,
    jvm_default = "all",
    warn = "off",
    visibility=["//visibility:public"],
    allow_kotlin_package = allow_kotlin_package,
    context_receivers = context_receivers,
    when_guards = when_guards,
  )