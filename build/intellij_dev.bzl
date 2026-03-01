"""Macros for IntelliJ-based IDE development builds."""

load("@rules_java//java:defs.bzl", "java_binary")

INTELLIJ_ADD_OPENS = [
    "java.base/java.io",
    "java.base/java.lang",
    "java.base/java.lang.ref",
    "java.base/java.lang.reflect",
    "java.base/java.net",
    "java.base/java.nio",
    "java.base/java.nio.charset",
    "java.base/java.text",
    "java.base/java.time",
    "java.base/java.util",
    "java.base/java.util.concurrent",
    "java.base/java.util.concurrent.atomic",
    "java.base/java.util.concurrent.locks",
    "java.base/jdk.internal.ref",
    "java.base/jdk.internal.vm",
    "java.base/sun.net.dns",
    "java.base/sun.nio",
    "java.base/sun.nio.ch",
    "java.base/sun.nio.fs",
    "java.base/sun.security.ssl",
    "java.base/sun.security.util",
    "java.desktop/com.apple.eawt",
    "java.desktop/com.apple.eawt.event",
    "java.desktop/com.apple.laf",
    "java.desktop/com.sun.java.swing",
    "java.desktop/com.sun.java.swing.plaf.gtk",
    "java.desktop/java.awt",
    "java.desktop/java.awt.dnd.peer",
    "java.desktop/java.awt.event",
    "java.desktop/java.awt.font",
    "java.desktop/java.awt.image",
    "java.desktop/java.awt.peer",
    "java.desktop/javax.swing",
    "java.desktop/javax.swing.plaf.basic",
    "java.desktop/javax.swing.text",
    "java.desktop/javax.swing.text.html",
    "java.desktop/javax.swing.text.html.parser",
    "java.desktop/sun.awt",
    "java.desktop/sun.awt.X11",
    "java.desktop/sun.awt.datatransfer",
    "java.desktop/sun.awt.image",
    "java.desktop/sun.awt.windows",
    "java.desktop/sun.font",
    "java.desktop/sun.java2d",
    "java.desktop/sun.lwawt",
    "java.desktop/sun.lwawt.macosx",
    "java.desktop/sun.swing",
    "java.management/sun.management",
    "jdk.attach/sun.tools.attach",
    "jdk.compiler/com.sun.tools.javac.api",
    "jdk.internal.jvmstat/sun.jvmstat.monitor",
    "jdk.jdi/com.sun.tools.jdi",
]

DEFAULT_JVM_FLAGS = [
    "-ea",
    "-Didea.jre.check=true",
    "-Didea.is.internal=true",
    "-Didea.debug.mode=true",
    "-Djava.system.class.loader=com.intellij.util.lang.PathClassLoader",
    "-Djava.nio.file.spi.DefaultFileSystemProvider=com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider",
]

def intellij_dev_binary(name, visibility, data, jvm_flags, env, platform_prefix, config_path, system_path, additional_modules, program_args):
    all_jvm_flags = DEFAULT_JVM_FLAGS + jvm_flags

    if platform_prefix:
        all_jvm_flags = all_jvm_flags + ["-Didea.platform.prefix=" + platform_prefix]

    # Use provided paths or defaults based on target name
    effective_config_path = config_path if config_path else "$${BUILD_WORKSPACE_DIRECTORY}/out/dev-data/" + name + "/config"
    effective_system_path = system_path if system_path else "$${BUILD_WORKSPACE_DIRECTORY}/out/dev-data/" + name + "/system"

    all_jvm_flags = all_jvm_flags + [
        "-Didea.config.path=" + effective_config_path,
        "-Didea.system.path=" + effective_system_path,
    ]

    # Allow to reset classpath from META-INF/MANIFEST.MF if classpath .jar due to classpath length limitations on Windows
    # https://github.com/bazelbuild/bazel/blob/93cde47ab3236b3b7124b41824f843f3659064de/src/tools/launcher/java_launcher.cc#L385
    all_jvm_flags += select({
        "@bazel_tools//src/conditions:windows": ["-Didea.reset.classpath.from.manifest=true"],
        "//conditions:default": [],
    })

    if additional_modules:
        all_jvm_flags = all_jvm_flags + ["-Dadditional.modules=\"" + additional_modules + "\""]

    java_binary(
        name = name,
        visibility = visibility,
        runtime_deps = ["@community//platform/bootstrap/dev"],
        main_class = "org.jetbrains.intellij.build.devServer.DevMainKt",
        data = data,
        jvm_flags = all_jvm_flags,
        env = env,
        add_opens = INTELLIJ_ADD_OPENS,
        args = program_args,
    )
