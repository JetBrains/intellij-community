/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.highlighting;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.ArrayUtil;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public abstract class DomElementsInspection<T extends DomElement> extends LocalInspectionTool {

  private final Class<? extends T>[] myDomClasses;

  public DomElementsInspection(Class<? extends T> domClass, @NotNull Class<? extends T>... additonalClasses) {
    myDomClasses = ArrayUtil.append(additonalClasses, domClass);
  }

  @Nullable
  public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (file instanceof XmlFile) {
      for (Class<? extends T> domClass: myDomClasses) {
        final DomFileElement<? extends T> fileElement = DomManager.getDomManager(file.getProject()).getFileElement((XmlFile)file, domClass);
        if (fileElement != null) {
          return checkDomFile((DomFileElement<T>)fileElement, manager, isOnTheFly);
        }
      }
    }
    return null;
  }

  @Nullable
  protected abstract ProblemDescriptor[] checkDomFile(@NotNull DomFileElement<T> fileElement, @NotNull InspectionManager manager, boolean isOnTheFly);

  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  public boolean isEnabledByDefault() {
    return true;
  }
}
