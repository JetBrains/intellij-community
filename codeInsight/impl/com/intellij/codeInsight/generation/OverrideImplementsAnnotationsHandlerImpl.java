/*
 * User: anna
 * Date: 19-Aug-2008
 */
package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;

public class OverrideImplementsAnnotationsHandlerImpl implements OverrideImplementsAnnotationsHandler {
  public String[] getAnnotations() {
    return new String[]{AnnotationUtil.NOT_NULL,AnnotationUtil.NLS};
  }

  @NotNull
  public String[] annotationsToRemove(@NotNull final String fqName) {
    if (Comparing.strEqual(fqName, AnnotationUtil.NOT_NULL)) {
      return new String[]{AnnotationUtil.NULLABLE};
    }
    if (Comparing.strEqual(fqName, AnnotationUtil.NLS)){
      return new String[]{AnnotationUtil.NON_NLS};
    }
    return new String[0];
  }
}