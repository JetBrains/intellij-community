package com.intellij.compilerOutputIndex.api.indexer;

import com.intellij.compilerOutputIndex.impl.MethodIncompleteSignature;
import com.intellij.openapi.project.Project;
import com.intellij.util.indexing.ID;

/**
 * @author Dmitry Batkovich
 */
public final class CompilerOutputIndexUtil {
  private CompilerOutputIndexUtil() {}

  public static <K, V> ID<K, V> generateIndexId(final String indexName, final Project project) {
    return ID.create(String.format("compilerOutputIndex.%s.%d", indexName, Math.abs(project.getBasePath().hashCode())));
  }

  public static boolean isSetterOrConstructorMethodName(final String methodName) {
    return MethodIncompleteSignature.CONSTRUCTOR_METHOD_NAME.equals(methodName) || methodName.startsWith("set");

  }
}
