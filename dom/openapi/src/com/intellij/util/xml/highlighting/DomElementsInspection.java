/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.highlighting;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.XmlSuppressableInspectionTool;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomElementVisitor;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomManager;
import com.intellij.openapi.application.ApplicationManager;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @author Dmitry Avdeev
 * @see com.intellij.util.xml.highlighting.BasicDomElementsInspection
 */
public abstract class DomElementsInspection<T extends DomElement> extends XmlSuppressableInspectionTool {

  private final Set<Class<? extends T>> myDomClasses;

  public DomElementsInspection(Class<? extends T> domClass, @NotNull Class<? extends T>... additonalClasses) {
    myDomClasses = new THashSet<Class<? extends T>>(Arrays.asList(additonalClasses));
    myDomClasses.add(domClass);
  }

  /**
   * This method is called internally in {@link com.intellij.util.xml.highlighting.DomElementAnnotationsManager#checkFileElement(com.intellij.util.xml.DomFileElement, DomElementsInspection)},
   * it should add some problems to the annotation holder. The default implementation performs recursive tree traversal, and calls
   * {@link #checkDomElement(com.intellij.util.xml.DomElement, DomElementAnnotationHolder, DomHighlightingHelper)} for each element. 
   * @param domFileElement file element to check
   * @param holder the place to store problems
   */
  public void checkFileElement(DomFileElement<T> domFileElement, final DomElementAnnotationHolder holder) {
    final DomHighlightingHelper helper =
      DomElementAnnotationsManager.getInstance(domFileElement.getManager().getProject()).getHighlightingHelper();
    domFileElement.accept(new DomElementVisitor() {
      public void visitDomElement(DomElement element) {
        if (element.getXmlElement() != null) {
          element.acceptChildren(this);
        }
        checkDomElement(element, holder, helper);
      }
    });
  }

  /**
   * @return the classes passed earlier to the constructor
   */
  public final Set<Class<? extends T>> getDomClasses() {
    return myDomClasses;
  }

  /**
   * Not intended to be overriden or called by implementors.
   * Override {@link #checkFileElement(com.intellij.util.xml.DomFileElement, DomElementAnnotationHolder)} (which is preferred) or
   * {@link #checkDomElement(com.intellij.util.xml.DomElement, DomElementAnnotationHolder, DomHighlightingHelper)} instead.
   */
  @Nullable
  public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (file instanceof XmlFile && (file.isPhysical() || ApplicationManager.getApplication().isUnitTestMode())) {
      for (Class<? extends T> domClass: myDomClasses) {
        final DomFileElement<? extends T> fileElement = DomManager.getDomManager(file.getProject()).getFileElement((XmlFile)file, domClass);
        if (fileElement != null) {
          return checkDomFile((DomFileElement<T>)fileElement, manager, isOnTheFly);
        }
      }
    }
    return null;
  }

  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  /**
   * not intended to be overriden or called by implementors
   */
  @Nullable
  protected ProblemDescriptor[] checkDomFile(@NotNull final DomFileElement<T> domFileElement,
                                             @NotNull final InspectionManager manager,
                                             final boolean isOnTheFly) {
    final DomElementAnnotationsManager annotationsManager = DomElementAnnotationsManager.getInstance(manager.getProject());

    final List<DomElementProblemDescriptor> list = annotationsManager.checkFileElement(domFileElement, this);
    List<ProblemDescriptor> problems =
      ContainerUtil.concat(list, new Function<DomElementProblemDescriptor, Collection<? extends ProblemDescriptor>>() {
        public Collection<ProblemDescriptor> fun(final DomElementProblemDescriptor s) {
          return annotationsManager.createProblemDescriptors(manager, s);
        }
      });
    return problems.toArray(new ProblemDescriptor[problems.size()]);
  }

  /**
   * Check particular DOM element for problems. The inspection implementor should focus on this method.
   * The default implementation throws {@link UnsupportedOperationException}.
   * See {@link com.intellij.util.xml.highlighting.BasicDomElementsInspection}
   * @param element element to check
   * @param holder a place to add problems to
   * @param helper helper object
   */
  protected void checkDomElement(DomElement element, DomElementAnnotationHolder holder, DomHighlightingHelper helper) {
    throw new UnsupportedOperationException("checkDomElement() is not implemented in " + getClass().getName());
  }
}
