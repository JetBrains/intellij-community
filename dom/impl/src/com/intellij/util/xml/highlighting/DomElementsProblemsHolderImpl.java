/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.highlighting;

import com.intellij.codeInsight.daemon.impl.analysis.XmlHighlightVisitor;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiLock;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.ResolveUtil;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DomElementsProblemsHolderImpl implements DomElementsProblemsHolder {
  private final Map<DomElement, List<DomElementProblemDescriptor>> myCachedErrors = new ConcurrentHashMap<DomElement, List<DomElementProblemDescriptor>>();
  private final Map<DomElement, List<DomElementProblemDescriptor>> myCachedXmlErrors = new ConcurrentHashMap<DomElement, List<DomElementProblemDescriptor>>();

  private final Map<DomElement, List<DomElementProblemDescriptor>> myCachedChildrenErrors =
    new ConcurrentHashMap<DomElement, List<DomElementProblemDescriptor>>();

  private final Map<DomElement, List<DomElementProblemDescriptor>> myCachedChildrenXmlErrors =
    new ConcurrentHashMap<DomElement, List<DomElementProblemDescriptor>>();

  private final Function<DomElement, Collection<DomElementProblemDescriptor>> myDomProblemsGetter =
    new Function<DomElement, Collection<DomElementProblemDescriptor>>() {
      public Collection<DomElementProblemDescriptor> fun(final DomElement s) {
        return notNullize(myCachedErrors.get(s));
      }
    };

  private final Function<DomElement, Collection<DomElementProblemDescriptor>> myXmlProblemsGetter =
    new Function<DomElement, Collection<DomElementProblemDescriptor>>() {
      public Collection<DomElementProblemDescriptor> fun(final DomElement s) {
        return notNullize(myCachedXmlErrors.get(s));
      }
    };

  private final DomFileElement myElement;

  private final Class<?> myRootType;

  public DomElementsProblemsHolderImpl(final DomFileElement element) {
    myElement = element;
    myRootType = DomReflectionUtil.getRawType(myElement.getRootElement().getDomElementType());
  }

  public final boolean calculateProblems(final DomElement mainElement) {
    synchronized (PsiLock.LOCK) {
      if (!myCachedErrors.containsKey(mainElement)) {
        final DomElementAnnotationHolderImpl holder = new DomElementAnnotationHolderImpl();
        ((DomElementAnnotationsManagerImpl)DomElementAnnotationsManager.getInstance(mainElement.getManager().getProject()))
          .annotate(mainElement, holder, myRootType);
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
            assert mainElement.equals(errorElement) : "DOM problem has been created for wrong element: " + descriptor + "\nRight element:" + mainElement;
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

  private static List<DomElementProblemDescriptor> notNullize(final List<DomElementProblemDescriptor> list) {
    return list == null ? Collections.<DomElementProblemDescriptor>emptyList() : list;
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
      final List<DomElementProblemDescriptor> list = myCachedXmlErrors.get(domElement);
      if (list != null) {
        problems.addAll(list);
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
    if (includeXmlProblems) {
      collection.addAll(getProblems(domElement, myCachedChildrenXmlErrors, myXmlProblemsGetter));
    }
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


  @NotNull
  private static List<DomElementProblemDescriptor> getResolveProblem(final GenericDomValue value) {
    final XmlElement element = value instanceof GenericAttributeValue ? ((GenericAttributeValue)value).getXmlAttributeValue() : value.getXmlTag();
    final Class aClass = value instanceof GenericAttributeValue ? XmlAttributeValue.class : XmlTag.class;
    if (element != null) {
      final List<DomElementProblemDescriptor> list = new SmartList<DomElementProblemDescriptor>();
      for (final PsiReference reference : ResolveUtil.getReferencesFromProviders(element, aClass)) {
        if (!reference.isSoft() && XmlHighlightVisitor.hasBadResolve(reference)) {
          list.add(new DomElementProblemDescriptorImpl(value, XmlHighlightVisitor.getErrorDescription(reference), HighlightSeverity.ERROR));
        }
      }
      return list;
    }
    return Collections.emptyList();
  }

  public List<DomElementProblemDescriptor> getAllProblems() {
    return getProblems(myElement, false, true);
  }
}
