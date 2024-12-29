load("@rules_kotlin//kotlin:jvm.bzl", "kt_javac_options")
load("@rules_jvm//:jvm.bzl", "kt_kotlinc_options")

def create_kotlinc_options(name, jvm_target, opt_in = [], allow_kotlin_package = False, context_receivers = False):
  kt_kotlinc_options(
    name = name,
    jvm_target = jvm_target,
    opt_in = [
      "com.intellij.openapi.util.IntellijInternalApi",
      # it is unusual to have such opt-ins for the entire monorepo,
      # but that is what JPS uses as the default - in bazel, let's not use it for all
      "org.jetbrains.kotlin.utils.addToStdlib.UnsafeCastFunction",
    ] + opt_in,
    jvm_default = "all",
    warn = "off",
    visibility=["//visibility:public"],
    allow_kotlin_package = allow_kotlin_package,
    context_receivers = context_receivers,
  )