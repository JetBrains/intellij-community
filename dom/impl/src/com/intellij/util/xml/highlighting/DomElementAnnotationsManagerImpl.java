/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.highlighting;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.PsiLock;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.containers.SoftHashMap;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomReflectionUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class DomElementAnnotationsManagerImpl extends DomElementAnnotationsManager implements ProjectComponent {
  private Map<Class, List<DomElementsAnnotator>> myClass2Annotator = new HashMap<Class, List<DomElementsAnnotator>>();

  private Map<DomFileElement, CachedValue<Boolean>> myCachedValues = new SoftHashMap<DomFileElement, CachedValue<Boolean>>();
  private Map<DomFileElement, Boolean> myCalculatingHolders = new SoftHashMap<DomFileElement, Boolean>();
  private Map<DomFileElement, DomElementsProblemsHolder> myReadyHolders = new SoftHashMap<DomFileElement, DomElementsProblemsHolder>();
  private static final DomElementsProblemsHolder EMPTY_PROBLEMS_HOLDER = new DomElementsProblemsHolder() {
    @NotNull
    public List<DomElementProblemDescriptor> getProblems(DomElement domElement) {
      return Collections.emptyList();
    }

    public List<DomElementProblemDescriptor> getProblems(final DomElement domElement, boolean includeXmlProblems) {
      return Collections.emptyList();
    }

    public List<DomElementProblemDescriptor> getProblems(final DomElement domElement,
                                                         final boolean includeXmlProblems,
                                                         final boolean withChildren) {
      return Collections.emptyList();
    }

    public List<DomElementProblemDescriptor> getProblems(DomElement domElement,
                                                         final boolean includeXmlProblems,
                                                         final boolean withChildren,
                                                         HighlightSeverity minSeverity) {
      return Collections.emptyList();
    }

    public List<DomElementProblemDescriptor> getAllProblems() {
      return Collections.emptyList();
    }

  };
  private final AnnotationBasedDomElementsAnnotator myAnnotationBasedDomElementsAnnotator = new AnnotationBasedDomElementsAnnotator();

  @NotNull
  public DomElementsProblemsHolder getProblemHolder(DomElement element) {
    if (element == null || !element.isValid()) return EMPTY_PROBLEMS_HOLDER;
    final DomFileElement<DomElement> fileElement = element.getRoot();
    synchronized (PsiLock.LOCK) {
      final DomElementsProblemsHolder readyHolder = myReadyHolders.get(fileElement);
      if (isHighlightingFinished(fileElement)) {
        return readyHolder;
      }
      if (myCalculatingHolders.containsKey(fileElement)) {
        return readyHolder == null ? EMPTY_PROBLEMS_HOLDER : readyHolder;
      }
      myCalculatingHolders.put(fileElement, Boolean.TRUE);
    }
    try {
      final DomElementsProblemsHolderImpl holder = new DomElementsProblemsHolderImpl(fileElement);
      holder.calculateAllProblems();
      synchronized (PsiLock.LOCK) {
        final Project project = fileElement.getManager().getProject();
        final CachedValuesManager cachedValuesManager = PsiManager.getInstance(project).getCachedValuesManager();
        myReadyHolders.put(fileElement, holder);
        final CachedValue<Boolean> cachedValue = cachedValuesManager.createCachedValue(new CachedValueProvider<Boolean>() {
          public Result<Boolean> compute() {
            return new Result<Boolean>(Boolean.FALSE, fileElement, ProjectRootManager.getInstance(project));
          }
        }, false);
        myCachedValues.put(fileElement, cachedValue);
        cachedValue.getValue();
      }
      return holder;
    } finally {
      synchronized (PsiLock.LOCK) {
        myCalculatingHolders.remove(fileElement);
      }
    }
  }

  private boolean isHighlightingFinished(final DomFileElement<DomElement> fileElement) {
    final CachedValue<Boolean> cachedValue = myCachedValues.get(fileElement);
    return myReadyHolders.containsKey(fileElement) && cachedValue != null && cachedValue.hasUpToDateValue();
  }

  @NotNull
  public DomElementsProblemsHolder getCachedProblemHolder(DomElement element) {
    if (element == null || !element.isValid()) return EMPTY_PROBLEMS_HOLDER;
    final DomFileElement fileElement = element.getRoot();
    synchronized (PsiLock.LOCK) {
      final DomElementsProblemsHolder holder = myReadyHolders.get(fileElement);
      return holder == null ? EMPTY_PROBLEMS_HOLDER : holder;
    }
  }

  public List<DomElementProblemDescriptor> getAllProblems(final DomFileElement<?> fileElement, HighlightSeverity minSeverity) {
    return getProblemHolder(fileElement).getProblems(fileElement, true, true, minSeverity);
  }

  public void annotate(final DomElement element, final DomElementAnnotationHolder holder) {
    final Class<?> type = DomReflectionUtil.getRawType(element.getDomElementType());
    final List<DomElementsAnnotator> list = myClass2Annotator.get(type);
    if (list != null) {
      for (DomElementsAnnotator annotator : list) {
        annotator.annotate(element, holder);
      }
    } else {
      myAnnotationBasedDomElementsAnnotator.annotate(element, holder);
    }
  }


  public final void registerDomElementsAnnotator(DomElementsAnnotator annotator, Class aClass) {
    getOrCreateAnnotators(aClass).add(annotator);
  }

  private List<DomElementsAnnotator> getOrCreateAnnotators(final Class aClass) {
    List<DomElementsAnnotator> annotators = myClass2Annotator.get(aClass);
    if (annotators == null) {
      annotators = new ArrayList<DomElementsAnnotator>();
      annotators.add(myAnnotationBasedDomElementsAnnotator);
      myClass2Annotator.put(aClass, annotators);
    }
    return annotators;
  }


  public List<ProblemDescriptor> createProblemDescriptors(final InspectionManager manager, DomElementProblemDescriptor problemDescriptor) {
    return DomElementsHighlightingUtil.createProblemDescriptors(manager, problemDescriptor);
  }

  public boolean isHighlightingFinished(final DomElement[] domElements) {
    for (final DomElement domElement : domElements) {
      final DomFileElement<DomElement> root = domElement.getRoot();
      synchronized (PsiLock.LOCK) {
        if (!isHighlightingFinished(root)) {
          return false;
        }
      }
    }
    return true;
  }

  @NotNull
  @NonNls
  public String getComponentName() {
    return "DomElementAnnotationsManager";
  }

  public void initComponent() {

  }

  public void disposeComponent() {

  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

}
