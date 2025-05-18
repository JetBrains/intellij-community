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

  OUT,
  ABI_OUT,

  KOTLIN_MODULE_NAME,

  API_VERSION,
  LANGUAGE_VERSION,
  JVM_TARGET,

  OPT_IN,
  ALLOW_KOTLIN_PACKAGE,
  WHEN_GUARDS,
  LAMBDAS,
  JVM_DEFAULT,
  INLINE_CLASSES,
  CONTEXT_RECEIVERS,

  WARN,

  FRIENDS,

  ADD_EXPORT,
  ADD_READS;

  public boolean isFlagSet(Map<CLFlags, ? extends Collection<String>> flags) {
    Collection<String> value = flags.get(this);
    return value != null && value.size() == 1 && Boolean.parseBoolean(value.iterator().next());
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
