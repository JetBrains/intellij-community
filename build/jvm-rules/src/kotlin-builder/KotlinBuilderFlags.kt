// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm.kotlin

internal enum class KotlinBuilderFlags {
  TARGET_LABEL,
  CLASSPATH,
  DIRECT_DEPENDENCIES,
  DEPS_ARTIFACTS,

  PLUGIN_ID,
  PLUGIN_CLASSPATH,

  OUTPUT,
  RULE_KIND,
  KOTLIN_MODULE_NAME,

  API_VERSION,
  LANGUAGE_VERSION,
  JVM_TARGET,

  OPT_IN,
  ALLOW_KOTLIN_PACKAGE,
  LAMBDAS,
  JVM_DEFAULT,
  INLINE_CLASSES,
  CONTEXT_RECEIVERS,

  WARN,

  KOTLIN_OUTPUT_SRCJAR,
  FRIEND_PATHS,
  KOTLIN_OUTPUT_JDEPS,
  TRACE,
  ABI_JAR,
  STRICT_KOTLIN_DEPS,
  REDUCED_CLASSPATH_MODE,
}