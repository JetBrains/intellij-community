/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.highlighting;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.util.xml.DomBundle;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class RemoveDomElementQuickFix implements LocalQuickFix {

  private final boolean myIsTag;
  private final String myName;

  public RemoveDomElementQuickFix(@NotNull DomElement element) {
    myIsTag = element.getXmlElement() instanceof XmlTag;
    myName = element.getXmlElementName();
  }

  @NotNull
  public String getName() {
    return myIsTag ?
           DomBundle.message("remove.element.fix.name", myName) :
           DomBundle.message("remove.attribute.fix.name", myName);
  }

  @NotNull
  public String getFamilyName() {
    return DomBundle.message("quick.fixes.family");
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    if (myIsTag) {
      final XmlTag tag = (XmlTag)descriptor.getPsiElement();
      final XmlTag parentTag = tag.getParentTag();
      final DomElement domElement = DomManager.getDomManager(project).getDomElement(tag);
      assert domElement != null;
      domElement.undefine();
      if (parentTag != null && parentTag.isValid()) {
        parentTag.collapseIfEmpty();
      }
    } else {
      final DomElement domElement = DomManager.getDomManager(project).getDomElement((XmlAttribute)descriptor.getPsiElement());
      assert domElement != null;
      domElement.undefine();
    }
  }
}
