"""A guard for the protobuf toolchains this build resolves.

Three invariants hold in this build. The protobuf code of the monorepo takes its protobuf-java runtime from a
prebuilt jar: java_proto_library adds the runtime of the resolved java proto toolchain to its generated code,
so that runtime must not be the library the protobuf module compiles from source. The root module decides
which prebuilt jar through its toolchain registration, and a module that is consumed as a dependency sees the
root's choice, so the guard checks the origin of the runtime, not its label. Protoc is never built from source, for
any target and in any configuration: the proto toolchain must supply a downloaded release binary. And the prebuilt
runtime is the release of the protobuf module: the rules_kotlin builder, consumed as a source archive, compiles its
own code against the module's source build of protobuf-java, which is legitimate, while its generated protos take
the prebuilt runtime of the toolchain. The builder's classpath carries both, so they must be one release. The guard
reads the release of the prebuilt jar from its Maven file name, protobuf-java-<version>.jar, and the release of the
module from @protobuf//:protobuf_version.bzl. The guard resolves the same two toolchains java_proto_library resolves
and fails the analysis when a result is not the prebuilt artifact of the module's release, and its message names
the cause. A second instance behind an exec transition checks the exec configuration, where the rules_kotlin
builder and its protos are built.
"""

load("@protobuf//:protobuf_version.bzl", "PROTOBUF_JAVA_VERSION")
load("@protobuf//bazel/common:proto_common.bzl", "proto_common")
load("@rules_java//java:defs.bzl", "JavaInfo")

_JAVA_PROTO_TOOLCHAIN_TYPE = Label("@protobuf//bazel/private:java_toolchain_type")
_PROTO_TOOLCHAIN_TYPE = Label("@protobuf//bazel/private:proto_toolchain_type")

# The repository of the protobuf module: a runtime from there is the source build (//java/core, //java/lite).
_PROTOBUF_REPO = Label("@protobuf//:protobuf_java").repo_name

# The Maven file name of the protobuf-java artifact is protobuf-java-<version>.jar.
_RUNTIME_JAR_PREFIX = "protobuf-java-"
_RUNTIME_JAR_SUFFIX = ".jar"

def _protobuf_java_version(jar):
    """The version in the Maven file name protobuf-java-<version>.jar, or None for another file."""
    name = jar.basename
    if not name.startswith(_RUNTIME_JAR_PREFIX) or not name.endswith(_RUNTIME_JAR_SUFFIX):
        return None
    version = name[len(_RUNTIME_JAR_PREFIX):-len(_RUNTIME_JAR_SUFFIX)]
    if not version or not version[0].isdigit():
        # Another artifact of the family, such as protobuf-java-util-<version>.jar.
        return None
    return version

def _proto_toolchain_guard_impl(ctx):
    if not proto_common.INCOMPATIBLE_ENABLE_PROTO_TOOLCHAIN_RESOLUTION:
        fail("proto toolchain resolution is disabled: java_proto_library then reads --proto_toolchain_for_java, " +
             "which names the source build of protobuf-java. Set --incompatible_enable_proto_toolchain_resolution.")

    runtime = ctx.toolchains[_JAVA_PROTO_TOOLCHAIN_TYPE].proto.runtime
    if runtime.label.repo_name == _PROTOBUF_REPO:
        fail(("java_proto_library resolves the protobuf-java runtime {actual}, which the protobuf module compiles " +
              "from source. The root module lost the registration of the java proto toolchain whose runtime is a " +
              "prebuilt jar; in this workspace that is register_toolchains(\"{registration}\").").format(
            actual = runtime.label,
            registration = ctx.attr.toolchain_registration,
        ))

    jars = runtime[JavaInfo].transitive_runtime_jars.to_list()
    versions = [v for v in [_protobuf_java_version(jar) for jar in jars] if v]
    if len(versions) != 1:
        fail(("The prebuilt protobuf-java runtime {actual} carries {count} protobuf-java-<version>.jar among its " +
              "runtime jars ({jars}). The guard reads the release of the runtime from that Maven file name and " +
              "needs exactly one such jar.").format(
            actual = runtime.label,
            count = len(versions),
            jars = ", ".join([jar.basename for jar in jars]),
        ))
    if versions[0] != PROTOBUF_JAVA_VERSION:
        fail(("The prebuilt protobuf-java runtime {actual} is release {version}, the protobuf module of this build " +
              "is release {expected} (@protobuf//:protobuf_version.bzl). The rules_kotlin builder compiles its own " +
              "code against the module's source build and its generated protos take this runtime, so its classpath " +
              "would carry two releases of protobuf-java. Pin the prebuilt jar and the protobuf module to one " +
              "release.").format(
            actual = runtime.label,
            version = versions[0],
            expected = PROTOBUF_JAVA_VERSION,
        ))

    protoc = ctx.toolchains[_PROTO_TOOLCHAIN_TYPE].proto.proto_compiler.executable
    if not protoc.is_source:
        fail(("protoc resolves to {owner}, a binary built from source. The build must run a downloaded protoc " +
              "release: set --@protobuf//bazel/toolchains:prefer_prebuilt_protoc=true, and check that protobuf " +
              "registers a prebuilt protoc for this platform.").format(owner = protoc.owner))

    return [DefaultInfo(files = depset(transitive = [ctx.attr.exec_check[DefaultInfo].files] if ctx.attr.exec_check else []))]

proto_toolchain_guard = rule(
    doc = """Fails the analysis when the java proto toolchain this build resolves has the protobuf module's source
    library as its runtime, when that prebuilt runtime is another release of protobuf-java than the protobuf module,
    or when the proto toolchain it resolves runs a protoc built from source. `exec_check` names a second
    instance that is analyzed in the exec configuration, so both configurations are checked. Build the target to
    check the toolchain registration of a root module.""",
    implementation = _proto_toolchain_guard_impl,
    attrs = {
        "toolchain_registration": attr.string(
            doc = "The label of the toolchain registration the message tells the reader to restore.",
            mandatory = True,
        ),
        "exec_check": attr.label(
            doc = "A guard instance analyzed in the exec configuration, where tools and their protos are built.",
            cfg = "exec",
        ),
    },
    toolchains = [
        config_common.toolchain_type(_JAVA_PROTO_TOOLCHAIN_TYPE, mandatory = True),
        config_common.toolchain_type(_PROTO_TOOLCHAIN_TYPE, mandatory = True),
    ],
)
