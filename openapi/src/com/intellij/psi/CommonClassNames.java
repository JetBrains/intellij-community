/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import org.jetbrains.annotations.NonNls;

/**
 * @author peter
 */
public interface CommonClassNames {
  @NonNls String JAVA_LANG_OBJECT = "java.lang.Object";
  @NonNls String JAVA_LANG_CLASS = "java.lang.Class";
  @NonNls String JAVA_LANG_STRING = "java.lang.String";
  @NonNls String JAVA_LANG_THROWABLE = "java.lang.Throwable";
  @NonNls String JAVA_LANG_ERROR = "java.lang.Error";
  @NonNls String JAVA_LANG_RUNTIME_EXCEPTION = "java.lang.RuntimeException";
  @NonNls String JAVA_LANG_ENUM = "java.lang.Enum";

  @NonNls String JAVA_UTIL_MAP = "java.util.Map";
  @NonNls String JAVA_UTIL_LIST = "java.util.List";
  @NonNls String JAVA_UTIL_SET = "java.util.Set";
  @NonNls String JAVA_UTIL_PROPERTIES = "java.util.Properties";
  @NonNls String JAVA_UTIL_PROPERTY_RESOURCE_BUNDLE = "java.util.PropertyResourceBundle";

}
