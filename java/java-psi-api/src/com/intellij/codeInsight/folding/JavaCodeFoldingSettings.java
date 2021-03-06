// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.folding;

import com.intellij.openapi.application.ApplicationManager;

public abstract class JavaCodeFoldingSettings {

  public static JavaCodeFoldingSettings getInstance() {
    return ApplicationManager.getApplication().getService(JavaCodeFoldingSettings.class);
  }

  public abstract boolean isCollapseImports();
  public abstract void setCollapseImports(boolean value);

  public abstract boolean isCollapseLambdas();
  public abstract void setCollapseLambdas(boolean value);

  public abstract boolean isCollapseMethods();
  public abstract void setCollapseMethods(boolean value);

  public abstract boolean isCollapseConstructorGenericParameters();
  public abstract void setCollapseConstructorGenericParameters(boolean value);

  public abstract boolean isCollapseAccessors();
  public abstract void setCollapseAccessors(boolean value);

  public abstract boolean isCollapseOneLineMethods();
  public abstract void setCollapseOneLineMethods(boolean value);

  public abstract boolean isCollapseInnerClasses();
  public abstract void setCollapseInnerClasses(boolean value);

  public abstract boolean isCollapseJavadocs();
  public abstract void setCollapseJavadocs(boolean value);

  public abstract boolean isCollapseFileHeader();
  public abstract void setCollapseFileHeader(boolean value);

  public abstract boolean isCollapseAnonymousClasses();
  public abstract void setCollapseAnonymousClasses(boolean value);

  public abstract boolean isCollapseAnnotations();
  public abstract void setCollapseAnnotations(boolean value);

  public abstract boolean isCollapseI18nMessages();
  public abstract void setCollapseI18nMessages(boolean value);

  public abstract boolean isCollapseSuppressWarnings();
  public abstract void setCollapseSuppressWarnings(boolean value);

  public abstract boolean isCollapseEndOfLineComments();
  public abstract void setCollapseEndOfLineComments(boolean value);

  public abstract boolean isCollapseMultilineComments();

  public abstract void setCollapseMultilineComments(boolean value);

  public abstract boolean isReplaceVarWithInferredType();
  public abstract void setReplaceVarWithInferredType(boolean value);
}
