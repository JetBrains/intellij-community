load(
  "@rules_kotlin//kotlin:jvm.bzl",
  _jvm_resources = "jvm_resources",
  _kt_jvm_library = "kt_jvm_library",
)

jvm_resources = _jvm_resources
jvm_library = _kt_jvm_library