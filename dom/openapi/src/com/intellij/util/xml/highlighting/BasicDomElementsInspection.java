/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.highlighting;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.Function;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.DomReflectionUtil;

import java.util.List;
import java.util.Collection;

/**
 * User: Sergey.Vasiliev
 */
public abstract class BasicDomElementsInspection extends DomElementsInspection {
  protected ProblemDescriptor[] findProblems(final PsiFile file, final InspectionManager manager, final boolean onTheFly) {
    List<ProblemDescriptor> problems =
      ContainerUtil.concat(getHolder(file), new Function<DomElementProblemDescriptor, Collection<ProblemDescriptor>>() {
        public Collection<ProblemDescriptor> fun(final DomElementProblemDescriptor s) {
          return DomElementAnnotationsManager.getInstance(file.getProject()).createProblemDescriptors(manager, s);
        }
      });
    return problems.toArray(new ProblemDescriptor[problems.size()]);
  }

  protected DomElementsProblemsHolder getHolder(final PsiFile file) {
    return DomElementAnnotationsManager.getInstance(file.getProject()).getProblemHolder(getRootElement(file));
  }

  protected boolean isDomFileElement(final PsiFile file, final Class rootElementClass) {
    if (file instanceof XmlFile) {
      final DomElement element = getRootElement(file);
      if (element != null && rootElementClass.isAssignableFrom(DomReflectionUtil.getRawType(element.getRoot().getRootElement().getDomElementType()))) {
        return true;
      }
    }
    return false;
  }

  protected DomElement getRootElement(final PsiFile file) {
    return DomManager.getDomManager(file.getProject()).getFileElement((XmlFile)file);
  }
}
