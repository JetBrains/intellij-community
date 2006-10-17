/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.highlighting;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.DomElementVisitor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.Function;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Set;
import java.util.List;
import java.util.Collection;

/**
 * @author Dmitry Avdeev
 */
public abstract class DomElementsInspection<T extends DomElement> extends LocalInspectionTool {

  private final Set<Class<? extends T>> myDomClasses;

  public DomElementsInspection(Class<? extends T> domClass, @NotNull Class<? extends T>... additonalClasses) {
    myDomClasses = new THashSet<Class<? extends T>>(Arrays.asList(additonalClasses));
    myDomClasses.add(domClass);
  }

  public void checkFileElement(DomFileElement<T> domFileElement, final DomElementAnnotationHolder holder) {
    final DomHighlightingHelper helper =
      DomElementAnnotationsManager.getInstance(domFileElement.getManager().getProject()).getHighlightingHelper();
    final Ref<Boolean> ref = new Ref<Boolean>(Boolean.FALSE);
    domFileElement.accept(new DomElementVisitor() {
      public void visitDomElement(DomElement element) {
        final Boolean old = ref.get();
        ref.set(Boolean.FALSE);
        element.acceptChildren(this);
        if (ref.get().booleanValue()) {
          ref.set(old);
          return;
        }
        final int oldSize = holder.getSize();
        checkDomElement(element, holder, helper);
        ref.set(oldSize != holder.getSize());
      }
    });
  }

  public final Set<Class<? extends T>> getDomClasses() {
    return myDomClasses;
  }

  /**
   * not intended to be overriden or called by implementors
   */
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

    final Project project = manager.getProject();
    final DomElementAnnotationsManager annotationsManager = DomElementAnnotationsManager.getInstance(project);
    final DomElementsProblemsHolder problemsHolder = annotationsManager.getProblemHolder(domFileElement);
    List<ProblemDescriptor> problems =
      ContainerUtil.concat(problemsHolder.getAllProblems(this), new Function<DomElementProblemDescriptor, Collection<? extends ProblemDescriptor>>() {
        public Collection<ProblemDescriptor> fun(final DomElementProblemDescriptor s) {
          return annotationsManager.createProblemDescriptors(manager, s);
        }
      });
    return problems.toArray(new ProblemDescriptor[problems.size()]);
  }

  protected void checkDomElement(DomElement element, DomElementAnnotationHolder holder, DomHighlightingHelper helper) {
    throw new UnsupportedOperationException();
  }
}
