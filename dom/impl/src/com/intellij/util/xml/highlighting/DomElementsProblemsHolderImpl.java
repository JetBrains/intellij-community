/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.highlighting;

import com.intellij.codeInsight.daemon.impl.analysis.XmlHighlightVisitor;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.containers.HashMap;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomElementVisitor;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class DomElementsProblemsHolderImpl extends SmartList<DomElementProblemDescriptor> implements DomElementsProblemsHolder {
  private HighlightSeverity myDefaultHighlightSeverity = HighlightSeverity.ERROR;
  private final Map<DomElement, List<DomElementProblemDescriptor>> myCachedErrors =
    new HashMap<DomElement, List<DomElementProblemDescriptor>>();
  private final Map<DomElement, List<DomElementProblemDescriptor>> myCachedXmlErrors =
    new HashMap<DomElement, List<DomElementProblemDescriptor>>();
  private final Map<DomElement, List<DomElementProblemDescriptor>> myCachedChildrenErrors =
    new HashMap<DomElement, List<DomElementProblemDescriptor>>();
  private final Map<DomElement, List<DomElementProblemDescriptor>> myCachedChildrenXmlErrors =
    new HashMap<DomElement, List<DomElementProblemDescriptor>>();
  private final Function<DomElement, Collection<DomElementProblemDescriptor>> myDomProblemsGetter =
    new Function<DomElement, Collection<DomElementProblemDescriptor>>() {
      public Collection<DomElementProblemDescriptor> fun(final DomElement s) {
        return getProblems(s);
      }
    };
  private final Function<DomElement, Collection<DomElementProblemDescriptor>> myXmlProblemsGetter =
    new Function<DomElement, Collection<DomElementProblemDescriptor>>() {
      public Collection<DomElementProblemDescriptor> fun(final DomElement s) {
        return getXmlProblems(s);
      }
    };

  public void createProblem(DomElement domElement, @Nullable String message) {
    createProblem(domElement, getDefaultHighlightSeverity(), message);
  }

  public void createProblem(DomElement domElement, DomCollectionChildDescription childDescription, @Nullable String message) {
    addProblem(new DomCollectionProblemDescriptorImpl(domElement, message, getDefaultHighlightSeverity(), childDescription));
  }

  @NotNull
  public synchronized List<DomElementProblemDescriptor> getProblems(DomElement domElement) {
    if (domElement == null || !domElement.isValid()) return Collections.emptyList();

    final List<DomElementProblemDescriptor> list = myCachedErrors.get(domElement);
    if (list != null) {
      return new SmartList<DomElementProblemDescriptor>(list);
    }

    List<DomElementProblemDescriptor> problems = new SmartList<DomElementProblemDescriptor>();
    for (DomElementProblemDescriptor problemDescriptor : this) {
      if (problemDescriptor.getDomElement().equals(domElement)) {
        problems.add(problemDescriptor);
      }
    }
    myCachedErrors.put(domElement, problems);
    return new SmartList<DomElementProblemDescriptor>(problems);
  }

  public List<DomElementProblemDescriptor> getProblems(final DomElement domElement, boolean includeXmlProblems) {
    List<DomElementProblemDescriptor> problems = getProblems(domElement);
    if (includeXmlProblems) {
      problems.addAll(getXmlProblems(domElement));
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

  public final void createProblem(DomElement domElement, HighlightSeverity highlightType, String message) {
    addProblem(new DomElementProblemDescriptorImpl(domElement, message, highlightType));
  }

  public void addProblem(final DomElementProblemDescriptor problemDescriptor) {
    add(problemDescriptor);
    myCachedChildrenErrors.clear();
    myCachedChildrenXmlErrors.clear();
    myCachedErrors.clear();
    myCachedXmlErrors.clear();
  }

  private Collection<DomElementProblemDescriptor> getXmlProblems(DomElement domElement) {
    final List<DomElementProblemDescriptor> list = myCachedXmlErrors.get(domElement);
    if (list != null) {
      return list;
    }

    List<DomElementProblemDescriptor> problems = new SmartList<DomElementProblemDescriptor>();
    if (domElement != null && domElement.getXmlTag() != null) {
      problems.addAll(getResolveProblems(domElement));
    }
    myCachedXmlErrors.put(domElement, problems);
    return problems;
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
    return problems;
  }


  private static Collection<DomElementProblemDescriptor> getResolveProblems(final DomElement domElement) {
    Collection<DomElementProblemDescriptor> problems = new ArrayList<DomElementProblemDescriptor>();
    final XmlElement xmlElement = domElement.getXmlElement();
    if (xmlElement != null) {
      for (PsiReference reference : getReferences(xmlElement)) {
        if (reference.isSoft()) {
          continue;
        }
        if (XmlHighlightVisitor.hasBadResolve(reference)) {
          final String description = XmlHighlightVisitor.getErrorDescription(reference);
          problems.add(new DomElementProblemDescriptorImpl(domElement, description, HighlightSeverity.ERROR));
        }
      }
    }
    return problems;
  }

  private static PsiReference[] getReferences(final XmlElement xmlElement) {
    if (xmlElement instanceof XmlAttribute) {
      final XmlAttributeValue value = ((XmlAttribute)xmlElement).getValueElement();
      return value == null ? PsiReference.EMPTY_ARRAY : value.getReferences();
    }
    return xmlElement.getReferences();
  }

  public List<DomElementProblemDescriptor> getAllProblems() {
    return this;
  }

  public HighlightSeverity getDefaultHighlightSeverity() {
    return myDefaultHighlightSeverity;
  }

  public void setDefaultHighlightSeverity(final HighlightSeverity defaultHighlightSeverity) {
    myDefaultHighlightSeverity = defaultHighlightSeverity;
  }
}
