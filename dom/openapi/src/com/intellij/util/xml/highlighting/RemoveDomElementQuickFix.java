/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.highlighting;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomBundle;
import com.intellij.util.xml.DomElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class RemoveDomElementQuickFix implements LocalQuickFix {

  private final DomElement myElement;

  public RemoveDomElementQuickFix(@NotNull DomElement element) {
    myElement = element.createStableCopy();
  }

  @NotNull
  public String getName() {
    final String name = myElement.getXmlElementName();
    return isTag() ?
           DomBundle.message("remove.element.fix.name", name) :
           DomBundle.message("remove.attribute.fix.name", name);
  }

  private boolean isTag() {
    return myElement.getXmlElement() instanceof XmlTag;
  }

  @NotNull
  public String getFamilyName() {
    return DomBundle.message("quick.fixes.family");
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    if (isTag()) {
      final DomElement parent = myElement.getParent();
      assert parent != null;
      final XmlTag parentTag = parent.getXmlTag();
      myElement.undefine();
      if (parentTag != null && parentTag.isValid()) {
        parentTag.collapseIfEmpty();
      }
    } else {
      myElement.undefine();
    }
  }
}
