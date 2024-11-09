load("@rules_kotlin//kotlin:jvm.bzl", "kt_javac_options")
load("@rules_kotlin//kotlin:core.bzl", "kt_kotlinc_options")

def create_javac_options(name, release):
  kt_javac_options(
    name = name,
    release = release,
    x_ep_disable_all_checks = True,
    visibility = ["//visibility:public"],
  )

def create_kotlinc_options(name, jvm_target, x_optin = []):
  kt_kotlinc_options(
    name = name,
    jvm_target = jvm_target,
    x_enable_incremental_compilation = True,
    x_optin = [
      "com.intellij.openapi.util.IntellijInternalApi",
      # it is unusual to have such opt-ins for the entire monorepo,
      # but that is what JPS uses as the default - in bazel, let's not use it for all
      #"org.jetbrains.kotlin.utils.addToStdlib.UnsafeCastFunction",
      #"org.jetbrains.kotlin.analysis.api.KaIdeApi",
      #"org.jetbrains.kotlin.analysis.api.KaNonPublicApi",
    ] + x_optin,
    x_jvm_default = "all",
    x_lambdas = "indy",
    warn = "off",
    visibility=["//visibility:public"],
    include_stdlibs = "none",
  )