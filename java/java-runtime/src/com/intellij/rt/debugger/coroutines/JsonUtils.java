// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.rt.debugger.coroutines;

import java.util.List;

public final class JsonUtils {
  private JsonUtils() { }

  static String dumpCoroutineStackTraceDumpToJson(
    List<StackTraceElement> continuationStackElements,
    List<List<String>> variableNames,
    List<List<String>> fieldNames,
    List<StackTraceElement> creationStack
  ) {
    StringBuilder result = new StringBuilder();
    result.append("{");
    dumpContinuationStacks(result, continuationStackElements, variableNames, fieldNames);
    if (creationStack != null) {
      result.append(",");
      dumpCreationStack(result, creationStack);
    }
    result.append("}");
    return result.toString();
  }

  private static void dumpContinuationStacks(StringBuilder result,
                                             List<StackTraceElement> continuationStackElements,
                                             List<List<String>> variableNames,
                                             List<List<String>> fieldNames) {
    result.append("\"continuationFrames\":");
    result.append("[");
    for (int i = 0; i < continuationStackElements.size(); i++) {
      if (i > 0) result.append(", ");
      result.append("{");
      dumpContinuationStack(result, continuationStackElements.get(i),
                            variableNames.get(i), fieldNames.get(i));
      result.append("}");
    }
    result.append("]");
  }

  private static void dumpContinuationStack(StringBuilder result,
                                            StackTraceElement continuationElement,
                                            List<String> variableNames,
                                            List<String> fieldNames) {
    if (continuationElement != null) {
      result.append("\"stackTraceElement\":");
      dumpStackTraceElement(result, continuationElement);
      result.append(", ");
    }
    result.append("\"spilledVariables\":");
    result.append("[");
    for (int i = 0; i < variableNames.size(); i++) {
      if (i > 0) result.append(", ");
      result.append("{");
      String fieldName = escapeJsonString(fieldNames.get(i));
      String variableName = escapeJsonString(variableNames.get(i));
      result.append("\"fieldName\":").append("\"").append(fieldName).append("\"");
      result.append(", \"variableName\":").append("\"").append(variableName).append("\"");
      result.append("}");
    }
    result.append("]");
  }

  private static void dumpCreationStack(StringBuilder result, List<StackTraceElement> creationStack) {
    result.append("\"creationStack\":");
    result.append("[");
    for (int i = 0; i < creationStack.size(); i++) {
      if (i > 0) result.append(", ");
      dumpStackTraceElement(result, creationStack.get(i));
    }
    result.append("]");
  }

  private static void dumpStackTraceElement(StringBuilder result, StackTraceElement element) {
    result.append("{");

    result.append("\"declaringClass\":");
    String className = escapeJsonString(element.getClassName());
    result.append("\"").append(className).append("\"");

    result.append(", \"methodName\":");
    String methodName = escapeJsonString(element.getMethodName());
    result.append("\"").append(methodName).append("\"");

    String fileName = element.getFileName();
    if (fileName != null) {
      fileName = escapeJsonString(fileName);
      result.append(", \"fileName\":");
      result.append("\"").append(fileName).append("\"");
    }

    result.append(", \"lineNumber\":");
    result.append(element.getLineNumber());

    result.append("}");
  }

  private static String escapeJsonString(String s) {
    StringBuilder builder = new StringBuilder();
    int length = s.length();
    for (int i = 0; i < length; i++) {
      char c = s.charAt(i);
      if (c == '"' || c == '\\' || c == '/') {
        builder.append('\\');
      }
      builder.append(c);
    }
    return builder.toString();
  }
}
