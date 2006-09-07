/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.highlighting;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiLock;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.containers.HashMap;
import com.intellij.util.xml.*;
import com.intellij.openapi.util.Ref;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class DomElementsProblemsHolderImpl implements DomElementsProblemsHolder {
  private final Map<DomElement, List<DomElementProblemDescriptor>> myCachedErrors = new THashMap<DomElement, List<DomElementProblemDescriptor>>();
  private final Map<DomElement, DomElementProblemDescriptor> myCachedXmlErrors = new THashMap<DomElement, DomElementProblemDescriptor>();

  private final Map<DomElement, List<DomElementProblemDescriptor>> myCachedChildrenErrors =
    new HashMap<DomElement, List<DomElementProblemDescriptor>>();

  private final Map<DomElement, List<DomElementProblemDescriptor>> myCachedChildrenXmlErrors =
    new HashMap<DomElement, List<DomElementProblemDescriptor>>();

  private final Function<DomElement, Collection<DomElementProblemDescriptor>> myDomProblemsGetter =
    new Function<DomElement, Collection<DomElementProblemDescriptor>>() {
      public Collection<DomElementProblemDescriptor> fun(final DomElement s) {
        final List<DomElementProblemDescriptor> list = myCachedErrors.get(s);
        return list == null ? Collections.<DomElementProblemDescriptor>emptyList() : list;
      }
    };

  private final Function<DomElement, Collection<DomElementProblemDescriptor>> myXmlProblemsGetter =
    new Function<DomElement, Collection<DomElementProblemDescriptor>>() {
      public Collection<DomElementProblemDescriptor> fun(final DomElement s) {
        final DomElementProblemDescriptor descriptor = myCachedXmlErrors.get(s);
        return descriptor == null ? Collections.<DomElementProblemDescriptor>emptyList() : Arrays.asList(descriptor);
      }
    };

  private final DomFileElement myElement;

  public DomElementsProblemsHolderImpl(final DomFileElement element) {
    myElement = element;
  }

  public final boolean calculateProblems(final DomElement mainElement) {
    synchronized (PsiLock.LOCK) {
      if (!myCachedErrors.containsKey(mainElement)) {
        final DomElementAnnotationHolderImpl holder = new DomElementAnnotationHolderImpl();
        ((DomElementAnnotationsManagerImpl)DomElementAnnotationsManager.getInstance(mainElement.getManager().getProject()))
          .annotate(mainElement, holder);
        final List<DomElementProblemDescriptor> result = new SmartList<DomElementProblemDescriptor>();
        boolean childrenHaveErrors = false;
        for (final DomElementProblemDescriptor descriptor : holder) {
          final DomElement errorElement = descriptor.getDomElement();
          if (mainElement.equals(errorElement.getParent())) {
            if (!myCachedErrors.containsKey(errorElement)) {
              myCachedErrors.put(errorElement, new SmartList<DomElementProblemDescriptor>());
            }
            myCachedErrors.get(errorElement).add(descriptor);
            childrenHaveErrors = true;
            result.clear();
          }
          else {
            assert mainElement.equals(errorElement) : descriptor;
            if (!childrenHaveErrors) {
              result.add(descriptor);
            }
          }
        }

        myCachedErrors.put(mainElement, result);
        if (mainElement instanceof GenericDomValue) {
          myCachedXmlErrors.put(mainElement, getResolveProblem((GenericDomValue)mainElement));
        }
      }
      return !myCachedErrors.get(mainElement).isEmpty();
    }
  }

  public final void calculateAllProblems() {
    final Ref<Boolean> ref = new Ref<Boolean>(Boolean.FALSE);
    myElement.accept(new DomElementVisitor() {
      public void visitDomElement(DomElement element) {
        final Boolean old = ref.get();
        ref.set(Boolean.FALSE);
        element.acceptChildren(this);
        ref.set(ref.get() ? old : calculateProblems(element));
      }
    });
  }

  @NotNull
  public synchronized List<DomElementProblemDescriptor> getProblems(DomElement domElement) {
    if (domElement == null || !domElement.isValid()) return Collections.emptyList();
    final SmartList<DomElementProblemDescriptor> result = new SmartList<DomElementProblemDescriptor>();
    final List<DomElementProblemDescriptor> list = myCachedErrors.get(domElement);
    if (list != null) result.addAll(list);
    return result;
  }

  public List<DomElementProblemDescriptor> getProblems(final DomElement domElement, boolean includeXmlProblems) {
    List<DomElementProblemDescriptor> problems = getProblems(domElement);
    if (includeXmlProblems) {
      final DomElementProblemDescriptor o = myCachedXmlErrors.get(domElement);
      if (o != null) {
        problems.add(o);
      }
    }
    return problems;
  }

  public List<DomElementProblemDescriptor> getProblems(final DomElement domElement,
                                                       final boolean includeXmlProblems,
                                                       final boolean withChildren) {

    final List<DomElementProblemDescriptor> list = getProblems(domElement);
    if (!withChildren || domElement == null || !domElement.isValid()) {
      return list;
    }

    final List<DomElementProblemDescriptor> collection = getProblems(domElement, myCachedChildrenErrors, myDomProblemsGetter);
    collection.addAll(getProblems(domElement, myCachedChildrenXmlErrors, myXmlProblemsGetter));
    return collection;
  }

  public List<DomElementProblemDescriptor> getProblems(DomElement domElement,
                                                       final boolean includeXmlProblems,
                                                       final boolean withChildren,
                                                       HighlightSeverity minSeverity) {
    List<DomElementProblemDescriptor> severityProblem = new ArrayList<DomElementProblemDescriptor>();
    for (DomElementProblemDescriptor problemDescriptor : getProblems(domElement, includeXmlProblems, withChildren)) {
      if (problemDescriptor.getHighlightSeverity().equals(minSeverity)) {
        severityProblem.add(problemDescriptor);
      }
    }

    return severityProblem;
  }

  private static List<DomElementProblemDescriptor> getProblems(final DomElement domElement,
                                                               final Map<DomElement, List<DomElementProblemDescriptor>> map,
                                                               final Function<DomElement, Collection<DomElementProblemDescriptor>> function) {
    final Collection<DomElementProblemDescriptor> list = map.get(domElement);
    if (list != null) {
      return new ArrayList<DomElementProblemDescriptor>(list);
    }

    final List<DomElementProblemDescriptor> problems = new ArrayList<DomElementProblemDescriptor>(function.fun(domElement));
    domElement.acceptChildren(new DomElementVisitor() {
      public void visitDomElement(DomElement element) {
        problems.addAll(getProblems(element, map, function));
      }
    });
    map.put(domElement, problems);
    return new ArrayList<DomElementProblemDescriptor>(problems);
  }


  @Nullable
  private static DomElementProblemDescriptor getResolveProblem(final GenericDomValue value) {
    if (value.getXmlElement() != null && value.getValue() == null) {
      final String description = value.getConverter().getErrorMessage(value.getStringValue(), new AbstractConvertContext() {
        @NotNull
        public DomElement getInvocationElement() {
          return value;
        }

        public PsiManager getPsiManager() {
          return PsiManager.getInstance(value.getManager().getProject());
        }
      });
      return new DomElementProblemDescriptorImpl(value, description, HighlightSeverity.ERROR);
    }
    return null;
  }

  public List<DomElementProblemDescriptor> getAllProblems() {
    return getProblems(myElement, false, true);
  }
}
