load("@rules_kotlin//kotlin/internal:opts.bzl", _kt_javac_options = "kt_javac_options")
load("//:rules/impl/kotlinc-options.bzl", _kt_kotlinc_options = "kt_kotlinc_options")
load(
    "//:rules/impl/transitions.bzl",
    _jvm_platform_transition = "jvm_platform_transition",
    _scrubbed_host_platform_transition = "scrubbed_host_platform_transition",
)
load("//:rules/import.bzl", _jvm_import = "jvm_import")
load("//:rules/library.bzl", _jvm_library = "jvm_library")
load("//:rules/provided-library.bzl", _jvm_provided_library = "jvm_provided_library")
load("//:rules/resource.bzl", _ResourceGroupInfo = "ResourceGroupInfo", _resourcegroup = "resourcegroup")

resourcegroup = _resourcegroup
jvm_library = _jvm_library

jvm_provided_library = _jvm_provided_library
jvm_import = _jvm_import

kt_javac_options = _kt_javac_options
kt_kotlinc_options = _kt_kotlinc_options

# for fleet_plugin_services_resources rule
ResourceGroupInfo = _ResourceGroupInfo
jvm_platform_transition = _jvm_platform_transition

# for rules outside this repository that run their own JVM worker: use it for the worker's deploy jar,
# so its exec path is host-independent and the actions using it hit the remote cache across platforms.
#
# This transition on its own is not enough: sharing an action's remote cache entries across Linux/macOS/Windows takes
# all three of the following, and skipping any one of them silently costs cache hits without breaking the build.
#  1. apply this transition to the attribute holding the worker's deploy jar, so the jar is built under a
#     host-independent output directory (bazel-out/scrubbed_host-*/) instead of bazel-out/<host>-opt-exec/;
#  2. do not declare the JBR files (`java_runtime.files`) as action inputs: their exec paths and digests are
#     host-specific, and scrubbing rewrites only arguments, never the input tree, so declaring them defeats it;
#  3. add the action's mnemonic to `build/bazel_scrubbing.cfg` (`community/build/bazel_scrubbing.cfg` in the
#     monorepo), so the remaining `java` executable argument is rewritten to a host-independent placeholder.
#
# `JvmCompile` in rules/impl/compile.bzl is the reference implementation. To verify, run
# `bazel aquery --include_commandline "mnemonic(<Mnemonic>, <target>)"`: no path in the action's inputs or
# arguments should mention the host platform, except the `java` executable that (3) scrubs.
scrubbed_host_platform_transition = _scrubbed_host_platform_transition
