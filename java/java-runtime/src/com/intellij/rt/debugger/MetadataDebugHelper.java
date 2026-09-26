// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.rt.debugger;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class MetadataDebugHelper {
  public static final String METADATA_SEPARATOR = "\n";

  private static final String METADATA_CLASS_NAME = "kotlin.Metadata";

  private static final String KIND = "kind";
  private static final String METADATA_VERSION = "metadataVersion";
  private static final String DATA_1 = "data1";
  private static final String DATA_2 = "data2";
  private static final String EXTRA_STRING = "extraString";
  private static final String PACKAGE_NAME = "packageName";
  private static final String EXTRA_INT = "extraInt";

  private static final String KIND_METHOD_NAME = "k";
  private static final String METADATA_VERSION_METHOD_NAME = "mv";
  private static final String DATA_1_METHOD_NAME = "d1";
  private static final String DATA_2_METHOD_NAME = "d2";
  private static final String EXTRA_STRING_METHOD_NAME = "xs";
  private static final String PACKAGE_NAME_METHOD_NAME = "pn";
  private static final String EXTRA_INT_METHOD_NAME = "xi";

  /*
   * Kotlin debugger uses this function to fetch @kotlin.Metadata for a given class.
   * Originally, reading annotations from the debugger is not supported by JVM.
   *
   * The metadata is returned as a string in JSON format, where the `data1` field is
   * base64 encoded to keep the format valid.
   *
   * Returning metadata as a single string allows us to minimize the number of JDI calls from
   * the debugger side. Any JDWP communication can be very expensive when debugging remotely,
   * especially when debugging Android applications on a phone.
   */
  public static String getDebugMetadataAsJson(Class<?> cls) {
    StringBuilder sb = new StringBuilder();
    appendDebugMetadataAsJson(sb, cls);
    return sb.toString();
  }

  @SuppressWarnings("unchecked")
  private static void appendDebugMetadataAsJson(StringBuilder sb, Class<?> cls) {
    try {
      Class<?> metadataClass = Class.forName(METADATA_CLASS_NAME, false, cls.getClassLoader());
      Method kindMethod = metadataClass.getDeclaredMethod(KIND_METHOD_NAME);
      Method metadataVersionMethod = metadataClass.getDeclaredMethod(METADATA_VERSION_METHOD_NAME);
      Method data1Method = metadataClass.getDeclaredMethod(DATA_1_METHOD_NAME);
      Method data2Method = metadataClass.getDeclaredMethod(DATA_2_METHOD_NAME);
      Method extraStringMethod = metadataClass.getDeclaredMethod(EXTRA_STRING_METHOD_NAME);
      Method packageNameMethod = metadataClass.getDeclaredMethod(PACKAGE_NAME_METHOD_NAME);
      Method extraIntMethod = metadataClass.getDeclaredMethod(EXTRA_INT_METHOD_NAME);

      Object metadata = cls.getAnnotation((Class<Annotation>)metadataClass);

      sb.append("{");

      int kind = (int)kindMethod.invoke(metadata);
      appendAsJsonValue(sb, KIND, kind);

      int[] metadataVersion = (int[]) metadataVersionMethod.invoke(metadata);
      appendAsJsonValue(sb, METADATA_VERSION, Arrays.toString(metadataVersion));

      String[] data1 = (String[])data1Method.invoke(metadata);
      appendAsJsonValue(sb, DATA_1, Arrays.toString(toJsonBase64Encoded(data1)));

      String[] data2 = (String[])data2Method.invoke(metadata);
      appendAsJsonValue(sb, DATA_2, Arrays.toString(toJson(data2)));

      String extraString = (String) extraStringMethod.invoke(metadata);
      appendAsJsonValue(sb, EXTRA_STRING, toJson(extraString));

      String packageName = (String) packageNameMethod.invoke(metadata);
      appendAsJsonValue(sb, PACKAGE_NAME, toJson(packageName));

      int extraInt = (int)extraIntMethod.invoke(metadata);
      appendAsJsonValueNoComma(sb, EXTRA_INT, extraInt);

      sb.append("}");
    } catch (Exception ignored) {
    }
  }

  /*
   * This function is used similarly to `getDebugMetadataAsJson`, when there is a need to
   * fetch metadata for multiple classes.
   *
   * The return value is a concatenation of metadata JSON representations of given classes
   * separated by MetadataDebugHelper.METADATA_SEPARATOR.
   */
  public static String getDebugMetadataListAsJson(Class<?>... classes) {
    StringBuilder sb = new StringBuilder();
    for (Class<?> cls : classes) {
      if (sb.length() != 0) {
        sb.append(METADATA_SEPARATOR);
      }
      appendDebugMetadataAsJson(sb, cls);
    }
    return sb.toString();
  }

  private static void appendAsJsonValue(StringBuilder sb, String name, Object value) {
    appendAsJsonValueNoComma(sb, name, value).append(',');
  }

  private static StringBuilder appendAsJsonValueNoComma(StringBuilder sb, String name, Object value) {
    return sb.append(toJson(name)).append(':').append(value);
  }

  private static String toJson(String str) {
    return "\"" + JsonUtils.escapeJsonString(str) + "\"";
  }

  private static String[] toJson(String[] array) {
    String[] result = new String[array.length];
    for (int i = 0; i < array.length; i++) {
      result[i] = toJson(array[i]);
    }
    return result;
  }

  @SuppressWarnings("PrimitiveArrayArgumentToVarargsMethod")
  private static String[] toJsonBase64Encoded(String[] array) throws Exception {
    Class<?> base64Class = Class.forName("java.util.Base64");
    Method getEncoding = base64Class.getDeclaredMethod("getEncoder");
    Object encoder = getEncoding.invoke(base64Class);
    Class<?> encoderClass = Class.forName("java.util.Base64$Encoder");
    Method encodeToString = encoderClass.getDeclaredMethod("encodeToString", byte[].class);

    String[] result = new String[array.length];
    for (int i = 0; i < array.length; i++) {
      byte[] bytes = array[i].getBytes(StandardCharsets.UTF_8);
      String encoded = (String)encodeToString.invoke(encoder, bytes);
      result[i] = toJson(encoded);
    }
    return result;
  }
}
