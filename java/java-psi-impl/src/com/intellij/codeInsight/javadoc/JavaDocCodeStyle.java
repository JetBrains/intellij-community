// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.javadoc;

import com.intellij.openapi.project.Project;

public abstract class JavaDocCodeStyle {
  public static JavaDocCodeStyle getInstance(Project project) {
    return project.getService(JavaDocCodeStyle.class);
  }

  public abstract boolean spaceBeforeComma();
  public abstract boolean spaceAfterComma();
}
