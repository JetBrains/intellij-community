// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

// todo: temporary copy of JvmBuilderFlags enum from the workers framework;
public enum CLFlags {
  NON_INCREMENTAL,
  JAVA_COUNT,
  TARGET_LABEL,
  // classpath
  CP,

  PLUGIN_ID,
  PLUGIN_CLASSPATH,
  PLUGIN_OPTIONS,

  OUT,
  ABI_OUT,

  KOTLIN_MODULE_NAME,

  API_VERSION,
  LANGUAGE_VERSION,
  JVM_TARGET,

  OPT_IN,
  X_ALLOW_KOTLIN_PACKAGE,
  X_ALLOW_RESULT_RETURN_TYPE,
  X_WHEN_GUARDS,
  X_LAMBDAS,
  JVM_DEFAULT,   // stable option
  X_JVM_DEFAULT, // deprecated option
  X_INLINE_CLASSES,
  X_CONTEXT_RECEIVERS,
  X_CONTEXT_PARAMETERS,
  X_CONSISTENT_DATA_CLASS_COPY_VISIBILITY,
  X_ALLOW_UNSTABLE_DEPENDENCIES,
  SKIP_METADATA_VERSION_CHECK,
  X_SKIP_PRERELEASE_CHECK,
  X_EXPLICIT_API_MODE,
  X_NO_CALL_ASSERTIONS,
  X_NO_PARAM_ASSERTIONS,
  X_SAM_CONVERSIONS,
  X_STRICT_JAVA_NULLABILITY_ASSERTIONS,
  X_WASM_ATTACH_JS_EXCEPTION,
  X_X_LANGUAGE,

  WARN,

  FRIENDS,

  ADD_EXPORT,
  ADD_READS;

  public boolean isFlagSet(Map<CLFlags, ? extends Collection<String>> flags) {
    Collection<String> value = flags.get(this);
    return value != null && (value.isEmpty() || value.size() == 1 && Boolean.parseBoolean(value.iterator().next()));
  }

  @Nullable
  public String getOptionalScalarValue(Map<CLFlags, ? extends Collection<String>> flags) {
    Collection<String> value = flags.get(this);
    if (value == null || value.isEmpty()) {
      return null;
    }
    if (value.size() > 1) {
      throw new IllegalArgumentException("Argument '" + this.name() + "' must have exactly one value: " + value);
    }
    return value.iterator().next();
  }

  @NotNull
  public String getMandatoryScalarValue(Map<CLFlags, ? extends Collection<String>> flags) {
    Collection<String> value = flags.get(this);
    if (value == null || value.isEmpty()) {
      throw new IllegalArgumentException("No value is set for the argument '" + this.name() + "'");
    }
    if (value.size() > 1) {
      throw new IllegalArgumentException("Argument '" + this.name() + "' must have exactly one value: " + value);
    }
    return value.iterator().next();
  }

  @NotNull
  public Iterable<String> getValue(Map<CLFlags, ? extends Collection<String>> flags) {
    Collection<String> value = flags.get(this);
    return value != null? value : List.of();
  }

  public void appendIfSet(Map<CLFlags, ? extends Collection<String>> flags, @Nullable String optionName, List<String> options) {
    Collection<String> value = flags.get(this);
    if (value != null && !value.isEmpty()) {
      if (optionName != null && !optionName.isBlank()) {
        options.add(optionName);
      }
      options.addAll(value);
    }
  }
}
