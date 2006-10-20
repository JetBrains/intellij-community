/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.highlighting;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Factory;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomElementVisitor;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomReflectionUtil;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DomElementsProblemsHolderImpl implements DomElementsProblemsHolder {
  private final Map<DomElement, Map<Class<? extends DomElementsInspection>, List<DomElementProblemDescriptor>>> myCachedErrors =
    new ConcurrentHashMap<DomElement, Map<Class<? extends DomElementsInspection>, List<DomElementProblemDescriptor>>>();
  private final Map<DomElement, Map<Class<? extends DomElementsInspection>, List<DomElementProblemDescriptor>>> myCachedChildrenErrors =
    new ConcurrentHashMap<DomElement, Map<Class<? extends DomElementsInspection>, List<DomElementProblemDescriptor>>>();

  private final Function<DomElement, List<DomElementProblemDescriptor>> myDomProblemsGetter =
    new Function<DomElement, List<DomElementProblemDescriptor>>() {
      public List<DomElementProblemDescriptor> fun(final DomElement s) {
        final Map<Class<? extends DomElementsInspection>, List<DomElementProblemDescriptor>> map = myCachedErrors.get(s);
        return map != null ? ContainerUtil.concat(map.values()) : Collections.<DomElementProblemDescriptor>emptyList();
      }
    };

  private final DomFileElement myElement;

  private final Class<? extends DomElement> myRootType;
  private static final Factory<Map<Class<? extends DomElementsInspection>,List<DomElementProblemDescriptor>>> CONCURRENT_HASH_MAP_FACTORY = new Factory<Map<Class<? extends DomElementsInspection>, List<DomElementProblemDescriptor>>>() {
    public Map<Class<? extends DomElementsInspection>, List<DomElementProblemDescriptor>> create() {
      return new ConcurrentHashMap<Class<? extends DomElementsInspection>, List<DomElementProblemDescriptor>>();
    }
  };
  private static final Factory<List<DomElementProblemDescriptor>> SMART_LIST_FACTORY = new Factory<List<DomElementProblemDescriptor>>() {
    public List<DomElementProblemDescriptor> create() {
      return new SmartList<DomElementProblemDescriptor>();
    }
  };

  public DomElementsProblemsHolderImpl(final DomFileElement element) {
    myElement = element;
    myRootType = (Class<? extends DomElement>)DomReflectionUtil.getRawType(myElement.getRootElement().getDomElementType());
  }

  public final void calculateAllProblems() {
    boolean hasInspections = false;
    final InspectionProjectProfileManager profileManager = InspectionProjectProfileManager.getInstance(myElement.getManager().getProject());
    final InspectionProfile profile = profileManager.getInspectionProfile(myElement.getFile());
    for (final InspectionProfileEntry profileEntry : profile.getInspectionTools()) {
      hasInspections |= processProfileEntry(profile.isToolEnabled(HighlightDisplayKey.find(profileEntry.getShortName())), profileEntry);
    }
    if (!hasInspections) {
      runInspection(new MockDomInspection(myRootType));
    }
  }

  private boolean processProfileEntry(final boolean isEnabled, final InspectionProfileEntry entry) {
    if (entry instanceof LocalInspectionToolWrapper) {
      return processProfileEntry(isEnabled, ((LocalInspectionToolWrapper)entry).getTool());
    }

    if (entry instanceof DomElementsInspection) {
      final DomElementsInspection inspection = (DomElementsInspection)entry;
      if (inspection.getDomClasses().contains(myRootType)) {
        if (isEnabled) {
          runInspection(inspection);
        }
        return true;
      }
    }
    return false;
  }

  private void runInspection(final DomElementsInspection<?> inspection) {
    ProgressManager.getInstance().checkCanceled();
    final DomElementAnnotationHolderImpl holder = new DomElementAnnotationHolderImpl();
    inspection.checkFileElement(myElement, holder);
    for (final DomElementProblemDescriptor descriptor : holder) {
      addProblem(descriptor, inspection.getClass());
    }
  }

  public final void addProblem(final DomElementProblemDescriptor descriptor, final Class<? extends DomElementsInspection> inspection) {
    ContainerUtil.getOrCreate(ContainerUtil.getOrCreate(myCachedErrors, descriptor.getDomElement(), CONCURRENT_HASH_MAP_FACTORY), inspection,
                              SMART_LIST_FACTORY).add(descriptor);
  }

  @NotNull
  public synchronized List<DomElementProblemDescriptor> getProblems(DomElement domElement) {
    if (domElement == null || !domElement.isValid()) return Collections.emptyList();
    return myDomProblemsGetter.fun(domElement);
  }

  public List<DomElementProblemDescriptor> getProblems(final DomElement domElement, boolean includeXmlProblems) {
    return getProblems(domElement);
  }

  public List<DomElementProblemDescriptor> getProblems(final DomElement domElement,
                                                       final boolean includeXmlProblems,
                                                       final boolean withChildren) {
    if (!withChildren || domElement == null || !domElement.isValid()) {
      return getProblems(domElement);
    }

    return ContainerUtil.concat(getProblemsMap(domElement).values());
  }

  public List<DomElementProblemDescriptor> getProblems(DomElement domElement,
                                                       final boolean includeXmlProblems,
                                                       final boolean withChildren,
                                                       final HighlightSeverity minSeverity) {
    return getProblems(domElement, withChildren, minSeverity);
  }

  public List<DomElementProblemDescriptor> getProblems(DomElement domElement, final boolean withChildren, final HighlightSeverity minSeverity) {
    return ContainerUtil.findAll(getProblems(domElement, true, withChildren), new Condition<DomElementProblemDescriptor>() {
      public boolean value(final DomElementProblemDescriptor object) {
        return object.getHighlightSeverity().compareTo(minSeverity) >= 0;
      }
    });

  }

  @NotNull
  private Map<Class<? extends DomElementsInspection>, List<DomElementProblemDescriptor>> getProblemsMap(final DomElement domElement) {
    final Map<Class<? extends DomElementsInspection>, List<DomElementProblemDescriptor>> map = myCachedChildrenErrors.get(domElement);
    if (map != null) {
      return map;
    }

    final Map<Class<? extends DomElementsInspection>, List<DomElementProblemDescriptor>> problems = new ConcurrentHashMap<Class<? extends DomElementsInspection>, List<DomElementProblemDescriptor>>();
    mergeMaps(problems, myCachedErrors.get(domElement));
    domElement.acceptChildren(new DomElementVisitor() {
      public void visitDomElement(DomElement element) {
        mergeMaps(problems, getProblemsMap(element));
      }
    });
    myCachedChildrenErrors.put(domElement, problems);
    return problems;
  }

  private static <T> void mergeMaps(final Map<T, List<DomElementProblemDescriptor>> accumulator, @Nullable final Map<T, List<DomElementProblemDescriptor>> toAdd) {
    if (toAdd == null) return;
    for (final Map.Entry<T, List<DomElementProblemDescriptor>> entry : toAdd.entrySet()) {
      ContainerUtil.getOrCreate(accumulator, entry.getKey(), SMART_LIST_FACTORY).addAll(entry.getValue());
    }
  }

  public List<DomElementProblemDescriptor> getAllProblems() {
    return getProblems(myElement, false, true);
  }

  public List<DomElementProblemDescriptor> getAllProblems(DomElementsInspection inspection) {
    if (!myElement.isValid()) {
      return Collections.emptyList();
    }
    final List<DomElementProblemDescriptor> list = getProblemsMap(myElement).get(inspection.getClass());
    return list != null ? new ArrayList<DomElementProblemDescriptor>(list) : Collections.<DomElementProblemDescriptor>emptyList();
  }
}
