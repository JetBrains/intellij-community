package com.intellij.tools.build.bazel.ijPluginPackager

/**
 * Represents an error in rule attributes or source files that should be reported as a build failure without printing the stacktrace
 */
internal class IjPluginPackagingException(message: String) : RuntimeException(message)
