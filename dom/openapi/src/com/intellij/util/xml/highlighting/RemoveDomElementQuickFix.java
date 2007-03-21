/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.highlighting;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomBundle;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class RemoveDomElementQuickFix implements LocalQuickFix {

  private final DomElement myElement;

  public RemoveDomElementQuickFix(@NotNull DomElement element) {
    myElement = element;
  }

  @NotNull
  public String getName() {
    final String name = myElement.getXmlElementName();
    return DomBundle.message("remove.element.fix.name", name);
  }

  @NotNull
  public String getFamilyName() {
    return DomBundle.message("quick.fixes.family");
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    myElement.undefine();
  }
}
