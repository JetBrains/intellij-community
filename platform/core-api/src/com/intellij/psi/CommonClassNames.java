// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

/**
 * @author peter
 */
public interface CommonClassNames {
  String DEFAULT_PACKAGE = "java.lang";

  String JAVA_LANG_OBJECT = "java.lang.Object";
  String JAVA_LANG_OBJECT_SHORT = "Object";
  String JAVA_LANG_CLASS = "java.lang.Class";
  String JAVA_LANG_OVERRIDE = "java.lang.Override";
  String JAVA_LANG_ENUM = "java.lang.Enum";
  String JAVA_LANG_VOID = "java.lang.Void";

  String JAVA_UTIL_OBJECTS = "java.util.Objects";

  String JAVA_LANG_THROWABLE = "java.lang.Throwable";
  String JAVA_LANG_EXCEPTION = "java.lang.Exception";
  String JAVA_LANG_ERROR = "java.lang.Error";
  String JAVA_LANG_ASSERTION_ERROR = "java.lang.AssertionError";
  String JAVA_LANG_RUNTIME_EXCEPTION = "java.lang.RuntimeException";
  String JAVA_LANG_AUTO_CLOSEABLE = "java.lang.AutoCloseable";

  String JAVA_LANG_ITERABLE = "java.lang.Iterable";
  String JAVA_UTIL_ITERATOR = "java.util.Iterator";

  String JAVA_LANG_RUNNABLE = "java.lang.Runnable";

  String JAVA_LANG_DEPRECATED = "java.lang.Deprecated";

  String JAVA_LANG_ANNOTATION_TARGET = "java.lang.annotation.Target";
  String JAVA_LANG_ANNOTATION_INHERITED = "java.lang.annotation.Inherited";
  String JAVA_LANG_ANNOTATION_ANNOTATION = "java.lang.annotation.Annotation";
  String JAVA_LANG_ANNOTATION_RETENTION = "java.lang.annotation.Retention";
  String JAVA_LANG_ANNOTATION_REPEATABLE = "java.lang.annotation.Repeatable";

  String JAVA_LANG_REFLECT_ARRAY = "java.lang.reflect.Array";

  String JAVA_UTIL_ARRAYS = "java.util.Arrays";
  String JAVA_UTIL_COLLECTIONS = "java.util.Collections";
  String JAVA_UTIL_COLLECTION = "java.util.Collection";
  String JAVA_UTIL_MAP = "java.util.Map";
  String JAVA_UTIL_MAP_ENTRY = "java.util.Map.Entry";
  String JAVA_UTIL_HASH_MAP = "java.util.HashMap";
  String JAVA_UTIL_CONCURRENT_HASH_MAP = "java.util.concurrent.ConcurrentHashMap";
  String JAVA_UTIL_LIST = "java.util.List";
  String JAVA_UTIL_ARRAY_LIST = "java.util.ArrayList";
  String JAVA_UTIL_SET = "java.util.Set";
  String JAVA_UTIL_HASH_SET = "java.util.HashSet";
  String JAVA_UTIL_PROPERTIES = "java.util.Properties";
  String JAVA_UTIL_PROPERTY_RESOURCE_BUNDLE = "java.util.PropertyResourceBundle";
  String JAVA_UTIL_DATE = "java.util.Date";
  String JAVA_UTIL_CALENDAR = "java.util.Calendar";
  String JAVA_UTIL_DICTIONARY = "java.util.Dictionary";
  String JAVA_UTIL_COMPARATOR = "java.util.Comparator";

  String JAVA_UTIL_OPTIONAL = "java.util.Optional";

  String JAVA_IO_SERIALIZABLE = "java.io.Serializable";
  String JAVA_IO_EXTERNALIZABLE = "java.io.Externalizable";
  String JAVA_IO_FILE = "java.io.File";

  String JAVA_LANG_STRING = "java.lang.String";
  String JAVA_LANG_STRING_SHORT = "String";
  String JAVA_LANG_NUMBER = "java.lang.Number";
  String JAVA_LANG_BOOLEAN = "java.lang.Boolean";
  String JAVA_LANG_BYTE = "java.lang.Byte";
  String JAVA_LANG_SHORT = "java.lang.Short";
  String JAVA_LANG_INTEGER = "java.lang.Integer";
  String JAVA_LANG_LONG = "java.lang.Long";
  String JAVA_LANG_FLOAT = "java.lang.Float";
  String JAVA_LANG_DOUBLE = "java.lang.Double";
  String JAVA_LANG_CHARACTER = "java.lang.Character";

  String JAVA_LANG_STRING_BUFFER = "java.lang.StringBuffer";
  String JAVA_LANG_STRING_BUILDER = "java.lang.StringBuilder";
  String JAVA_LANG_ABSTRACT_STRING_BUILDER = "java.lang.AbstractStringBuilder";

  String JAVA_LANG_MATH = "java.lang.Math";
  String JAVA_LANG_STRICT_MATH = "java.lang.StrictMath";

  String JAVA_LANG_CLONEABLE = "java.lang.Cloneable";
  String JAVA_LANG_COMPARABLE = "java.lang.Comparable";

  String JAVA_LANG_SAFE_VARARGS = "java.lang.SafeVarargs";
  String JAVA_LANG_FUNCTIONAL_INTERFACE = "java.lang.FunctionalInterface";

  String JAVA_LANG_NULL_POINTER_EXCEPTION = "java.lang.NullPointerException";

  String JAVA_UTIL_CONCURRENT_FUTURE = "java.util.concurrent.Future";
  String JAVA_UTIL_CONCURRENT_CALLABLE = "java.util.concurrent.Callable";

  String JAVA_UTIL_STREAM_BASE_STREAM = "java.util.stream.BaseStream";
  String JAVA_UTIL_STREAM_STREAM = "java.util.stream.Stream";
  String JAVA_UTIL_STREAM_INT_STREAM = "java.util.stream.IntStream";
  String JAVA_UTIL_STREAM_LONG_STREAM = "java.util.stream.LongStream";
  String JAVA_UTIL_STREAM_DOUBLE_STREAM = "java.util.stream.DoubleStream";
  String JAVA_UTIL_STREAM_COLLECTORS = "java.util.stream.Collectors";
  String JAVA_UTIL_FUNCTION_PREDICATE = "java.util.function.Predicate";
  String JAVA_UTIL_FUNCTION_CONSUMER = "java.util.function.Consumer";
  String JAVA_UTIL_FUNCTION_FUNCTION = "java.util.function.Function";
  String JAVA_UTIL_FUNCTION_BI_FUNCTION = "java.util.function.BiFunction";

  String JAVA_LANG_INVOKE_MH_POLYMORPHIC = "java.lang.invoke.MethodHandle.PolymorphicSignature";

  String CLASS_FILE_EXTENSION = ".class";

  //<editor-fold desc="Deprecated stuff.">
  /** @deprecated use {@link #JAVA_UTIL_FUNCTION_BI_FUNCTION} (to be removed in IDEA 2019) */
  @SuppressWarnings("SpellCheckingInspection")
  String JAVA_UTIL_FUNCTION_BIFUNCTION = JAVA_UTIL_FUNCTION_BI_FUNCTION;
  //</editor-fold>
}