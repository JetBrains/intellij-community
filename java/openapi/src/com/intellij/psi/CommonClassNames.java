/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
  @NonNls String JAVA_LANG_ANNOTATION_ANNOTATION = "java.lang.annotation.Annotation";
  @NonNls String JAVA_LANG_ERROR = "java.lang.Error";
  @NonNls String JAVA_LANG_RUNTIME_EXCEPTION = "java.lang.RuntimeException";
  @NonNls String JAVA_LANG_ENUM = "java.lang.Enum";
  @NonNls String JAVA_LANG_ITERABLE = "java.lang.Iterable";

  @NonNls String JAVA_LANG_DEPRECATED = "java.lang.Deprecated";
  @NonNls String JAVA_LANG_ANNOTATION_INHERITED = "java.lang.annotation.Inherited";

  @NonNls String JAVA_LANG_REFLECT_ARRAY = "java.lang.reflect.Array";

  @NonNls String JAVA_UTIL_ARRAYS = "java.util.Arrays";
  @NonNls String JAVA_UTIL_COLLECTIONS = "java.util.Collections";
  @NonNls String JAVA_UTIL_COLLECTION = "java.util.Collection";
  @NonNls String JAVA_UTIL_MAP = "java.util.Map";
  @NonNls String JAVA_UTIL_LIST = "java.util.List";
  @NonNls String JAVA_UTIL_SET = "java.util.Set";
  @NonNls String JAVA_UTIL_PROPERTIES = "java.util.Properties";
  @NonNls String JAVA_UTIL_PROPERTY_RESOURCE_BUNDLE = "java.util.PropertyResourceBundle";
  @NonNls String JAVA_UTIL_DATE = "java.util.Date";
  @NonNls String JAVA_SQL_DATE = "java.sql.Date";
  @NonNls String JAVA_UTIL_CALENDAR = "java.util.Calendar";
  @NonNls String JAVA_UTIL_DICTIONARY = "java.util.Dictionary";
  @NonNls String JAVA_UTIL_COMPARATOR = "java.util.Comparator";

  @NonNls String JAVA_IO_SERIALIZABLE = "java.io.Serializable";
  @NonNls String JAVA_IO_EXTERNALIZABLE = "java.io.Externalizable";

  @NonNls String JAVA_LANG_NUMBER = "java.lang.Number";

  @NonNls String JAVA_LANG_BOOLEAN = "java.lang.Boolean";
  @NonNls String JAVA_LANG_BYTE = "java.lang.Byte";
  @NonNls String JAVA_LANG_SHORT = "java.lang.Short";
  @NonNls String JAVA_LANG_INTEGER = "java.lang.Integer";
  @NonNls String JAVA_LANG_LONG = "java.lang.Long";
  @NonNls String JAVA_LANG_FLOAT = "java.lang.Float";
  @NonNls String JAVA_LANG_DOUBLE = "java.lang.Double";
  @NonNls String JAVA_LANG_CHARACTER = "java.lang.Character";

  @NonNls String JAVA_LANG_STRING_BUFFER = "java.lang.StringBuffer";
  @NonNls String JAVA_LANG_ABSTRACT_STRING_BUILDER = "java.lang.AbstractStringBuilder";

  @NonNls String JAVA_LANG_EXCEPTION = "java.lang.Exception";

  @NonNls String JAVA_LANG_CLONEABLE = "java.lang.Cloneable";
  @NonNls String JAVA_LANG_COMPARABLE = "java.lang.Comparable";
  @NonNls String CLASS_FILE_EXTENSION = ".class";

  @NonNls String JAVA_LANG_STRING_SHORT = "String";

  @NonNls String JAVA_UTIL_CONCURRENT_FUTURE = "java.util.concurrent.Future";
}
