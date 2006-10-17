/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml.highlighting;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author peter
 */
public abstract class DomHighlightingHelper {

  @NotNull
  public abstract List<DomElementProblemDescriptor> checkRequired(DomElement element, DomElementAnnotationHolder holder);

  @NotNull
  public abstract List<DomElementProblemDescriptor> checkExtendClass(GenericDomValue element, DomElementAnnotationHolder holder);

  @NotNull
  public abstract List<DomElementResolveProblemDescriptor> checkResolveProblems(GenericDomValue element, DomElementAnnotationHolder holder);

  @NotNull
  public abstract List<DomElementProblemDescriptor> checkNameIdentity(DomElement element, DomElementAnnotationHolder holder);

  public abstract void runAnnotators(DomElement element, DomElementAnnotationHolder holder, Class<? extends DomElement> rootClass);
}
