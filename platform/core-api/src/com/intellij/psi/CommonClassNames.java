/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
  @NonNls String DEFAULT_PACKAGE = "java.lang";

  @NonNls String JAVA_LANG_OBJECT = "java.lang.Object";
  @NonNls String JAVA_LANG_OBJECT_SHORT = "Object";
  @NonNls String JAVA_LANG_CLASS = "java.lang.Class";
  @NonNls String JAVA_LANG_OVERRIDE = "java.lang.Override";
  @NonNls String JAVA_LANG_ENUM = "java.lang.Enum";
  @NonNls String JAVA_LANG_VOID = "java.lang.Void";

  @NonNls String JAVA_UTIL_OBJECTS = "java.util.Objects";

  @NonNls String JAVA_LANG_THROWABLE = "java.lang.Throwable";
  @NonNls String JAVA_LANG_EXCEPTION = "java.lang.Exception";
  @NonNls String JAVA_LANG_ERROR = "java.lang.Error";
  @NonNls String JAVA_LANG_ASSERTION_ERROR = "java.lang.AssertionError";
  @NonNls String JAVA_LANG_RUNTIME_EXCEPTION = "java.lang.RuntimeException";
  @NonNls String JAVA_LANG_AUTO_CLOSEABLE = "java.lang.AutoCloseable";

  @NonNls String JAVA_LANG_ITERABLE = "java.lang.Iterable";
  @NonNls String JAVA_UTIL_ITERATOR = "java.util.Iterator";

  @NonNls String JAVA_LANG_RUNNABLE = "java.lang.Runnable";

  @NonNls String JAVA_LANG_DEPRECATED = "java.lang.Deprecated";

  @NonNls String JAVA_LANG_ANNOTATION_TARGET = "java.lang.annotation.Target";
  @NonNls String JAVA_LANG_ANNOTATION_INHERITED = "java.lang.annotation.Inherited";
  @NonNls String JAVA_LANG_ANNOTATION_ANNOTATION = "java.lang.annotation.Annotation";
  @NonNls String JAVA_LANG_ANNOTATION_RETENTION = "java.lang.annotation.Retention";
  @NonNls String JAVA_LANG_ANNOTATION_REPEATABLE = "java.lang.annotation.Repeatable";

  @NonNls String JAVA_LANG_REFLECT_ARRAY = "java.lang.reflect.Array";

  @NonNls String JAVA_UTIL_ARRAYS = "java.util.Arrays";
  @NonNls String JAVA_UTIL_COLLECTIONS = "java.util.Collections";
  @NonNls String JAVA_UTIL_COLLECTION = "java.util.Collection";
  @NonNls String JAVA_UTIL_MAP = "java.util.Map";
  @NonNls String JAVA_UTIL_MAP_ENTRY = "java.util.Map.Entry";
  @NonNls String JAVA_UTIL_HASH_MAP = "java.util.HashMap";
  @NonNls String JAVA_UTIL_CONCURRENT_HASH_MAP = "java.util.concurrent.ConcurrentHashMap";
  @NonNls String JAVA_UTIL_LIST = "java.util.List";
  @NonNls String JAVA_UTIL_ARRAY_LIST = "java.util.ArrayList";
  @NonNls String JAVA_UTIL_SET = "java.util.Set";
  @NonNls String JAVA_UTIL_HASH_SET = "java.util.HashSet";
  @NonNls String JAVA_UTIL_PROPERTIES = "java.util.Properties";
  @NonNls String JAVA_UTIL_PROPERTY_RESOURCE_BUNDLE = "java.util.PropertyResourceBundle";
  @NonNls String JAVA_UTIL_DATE = "java.util.Date";
  @NonNls String JAVA_UTIL_CALENDAR = "java.util.Calendar";
  @NonNls String JAVA_UTIL_DICTIONARY = "java.util.Dictionary";
  @NonNls String JAVA_UTIL_COMPARATOR = "java.util.Comparator";

  @NonNls String JAVA_UTIL_OPTIONAL = "java.util.Optional";

  @NonNls String JAVA_IO_SERIALIZABLE = "java.io.Serializable";
  @NonNls String JAVA_IO_EXTERNALIZABLE = "java.io.Externalizable";
  @NonNls String JAVA_IO_FILE = "java.io.File";

  @NonNls String JAVA_LANG_STRING = "java.lang.String";
  @NonNls String JAVA_LANG_STRING_SHORT = "String";
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
  @NonNls String JAVA_LANG_STRING_BUILDER = "java.lang.StringBuilder";
  @NonNls String JAVA_LANG_ABSTRACT_STRING_BUILDER = "java.lang.AbstractStringBuilder";

  @NonNls String JAVA_LANG_MATH = "java.lang.Math";
  @NonNls String JAVA_LANG_STRICT_MATH = "java.lang.StrictMath";

  @NonNls String JAVA_LANG_CLONEABLE = "java.lang.Cloneable";
  @NonNls String JAVA_LANG_COMPARABLE = "java.lang.Comparable";

  @NonNls String JAVA_LANG_NULL_POINTER_EXCEPTION = "java.lang.NullPointerException";

  @NonNls String JAVA_UTIL_CONCURRENT_FUTURE = "java.util.concurrent.Future";
  @NonNls String JAVA_UTIL_CONCURRENT_CALLABLE = "java.util.concurrent.Callable";

  @NonNls String JAVA_UTIL_STREAM_BASE_STREAM = "java.util.stream.BaseStream";
  @NonNls String JAVA_UTIL_STREAM_STREAM = "java.util.stream.Stream";
  @NonNls String JAVA_UTIL_STREAM_INT_STREAM = "java.util.stream.IntStream";
  @NonNls String JAVA_UTIL_STREAM_LONG_STREAM = "java.util.stream.LongStream";
  @NonNls String JAVA_UTIL_STREAM_DOUBLE_STREAM = "java.util.stream.DoubleStream";
  @NonNls String JAVA_UTIL_STREAM_COLLECTORS = "java.util.stream.Collectors";
  @NonNls String JAVA_UTIL_FUNCTION_PREDICATE = "java.util.function.Predicate";
  @NonNls String JAVA_UTIL_FUNCTION_CONSUMER = "java.util.function.Consumer";
  @NonNls String JAVA_UTIL_FUNCTION_FUNCTION = "java.util.function.Function";
  @NonNls String JAVA_UTIL_FUNCTION_BIFUNCTION = "java.util.function.BiFunction";

  @NonNls String JAVA_LANG_INVOKE_MH_POLYMORPHIC = "java.lang.invoke.MethodHandle.PolymorphicSignature";

  @NonNls String CLASS_FILE_EXTENSION = ".class";
  @NonNls String JAVA_LANG_FUNCTIONAL_INTERFACE = "java.lang.FunctionalInterface";
}
