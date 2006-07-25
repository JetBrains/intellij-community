/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.highlighting;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.containers.WeakFactoryMap;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomReflectionUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class DomElementAnnotationsManagerImpl extends DomElementAnnotationsManager implements ProjectComponent {
  private Map<Class, List<DomElementsAnnotator>> myClass2Annotator = new HashMap<Class, List<DomElementsAnnotator>>();

  private WeakFactoryMap<DomFileElement, CachedValue<DomElementsProblemsHolder>> myCache =
    new WeakFactoryMap<DomFileElement, CachedValue<DomElementsProblemsHolder>>() {
      protected CachedValue<DomElementsProblemsHolder> create(final DomFileElement fileElement) {
        return getCachedValue(fileElement);
      }
    };
  private static final DomElementsProblemsHolderImpl EMPTY_PROBLEMS_HOLDER = new DomElementsProblemsHolderImpl() {
    public void addProblem(final DomElementProblemDescriptor problemDescriptor) {
      throw new UnsupportedOperationException("This holder is immutable");
    }

    @NotNull
    public synchronized List<DomElementProblemDescriptor> getProblems(DomElement domElement) {
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
    return getDomElementsProblemsHolder(element.getRoot());
  }

  @NotNull
  public DomElementsProblemsHolder getCachedProblemHolder(DomElement element) {
    if (element == null || !element.isValid()) return EMPTY_PROBLEMS_HOLDER;
    final DomFileElement fileElement = element.getRoot();
    final CachedValue<DomElementsProblemsHolder> cachedValue = myCache.get(fileElement);
    return cachedValue == null || !cachedValue.hasUpToDateValue() ? EMPTY_PROBLEMS_HOLDER : cachedValue.getValue();
  }

  public List<DomElementProblemDescriptor> getAllProblems(final DomFileElement<?> fileElement, HighlightSeverity minSeverity) {
    return getDomElementsProblemsHolder(fileElement).getAllProblems();
  }

  public DomElementsProblemsHolder getDomElementsProblemsHolder(final DomFileElement<?> fileElement) {
    return myCache.get(fileElement).getValue();
  }

  private CachedValue<DomElementsProblemsHolder> getCachedValue(final DomFileElement fileElement) {
    final CachedValuesManager cachedValuesManager = PsiManager.getInstance(fileElement.getManager().getProject()).getCachedValuesManager();

    return cachedValuesManager.createCachedValue(new CachedValueProvider<DomElementsProblemsHolder>() {
      public Result<DomElementsProblemsHolder> compute() {
        final DomElementsProblemsHolder holder = new DomElementsProblemsHolderImpl();
        final DomElement rootElement = fileElement.getRootElement();
        final Class<?> type = DomReflectionUtil.getRawType(rootElement.getDomElementType());
        final List<DomElementsAnnotator> list = myClass2Annotator.get(type);
        if (list != null) {
          for (DomElementsAnnotator annotator : list) {
            annotator.annotate(rootElement, holder);
          }
        } else {
          myAnnotationBasedDomElementsAnnotator.annotate(rootElement, holder);
        }
        return new Result<DomElementsProblemsHolder>(holder, fileElement, ProjectRootManager.getInstance(fileElement.getManager().getProject()));
      }
    }, false);
  }


  public void registerDomElementsAnnotator(DomElementsAnnotator annotator, Class aClass) {
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
      final DomElementsProblemsHolder holder = getCachedProblemHolder(domElement);
      if (holder == EMPTY_PROBLEMS_HOLDER) return false;
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
