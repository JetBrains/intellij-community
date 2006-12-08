/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.highlighting;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.profile.Profile;
import com.intellij.profile.ProfileChangeAdapter;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.psi.PsiLock;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.SoftHashMap;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class DomElementAnnotationsManagerImpl extends DomElementAnnotationsManager implements ProjectComponent {
  private final EventDispatcher<DomHighlightingListener> myDispatcher = EventDispatcher.create(DomHighlightingListener.class);

  private final Map<Class, List<DomElementsAnnotator>> myClass2Annotator = new HashMap<Class, List<DomElementsAnnotator>>();

  private final Map<DomFileElement, CachedValue<Boolean>> myCachedValues = new SoftHashMap<DomFileElement, CachedValue<Boolean>>();
  private final Map<DomFileElement, Boolean> myCalculatingHolders = new SoftHashMap<DomFileElement, Boolean>();
  private final Map<DomFileElement, DomElementsProblemsHolder> myReadyHolders = new SoftHashMap<DomFileElement, DomElementsProblemsHolder>();
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

    public List<DomElementProblemDescriptor> getProblems(DomElement domElement, final boolean withChildren, HighlightSeverity minSeverity) {
      return Collections.emptyList();
    }

    public List<DomElementProblemDescriptor> getAllProblems() {
      return Collections.emptyList();
    }

    public List<DomElementProblemDescriptor> getAllProblems(DomElementsInspection inspection) {
      return Collections.emptyList();
    }

  };
  private final DomHighlightingHelperImpl myHighlightingHelper = new DomHighlightingHelperImpl(this);
  private final ModificationTracker myModificationTracker;

  public DomElementAnnotationsManagerImpl(Project project, InspectionProfileManager manager) {
    final int[] modCount = new int[]{0};
    myModificationTracker = new ModificationTracker() {
      public long getModificationCount() {
        return modCount[0];
      }
    };
    manager.addProfileChangeListener(new ProfileChangeAdapter() {
      public void profileActivated(@Nullable NamedScope scope, Profile oldProfile, Profile profile) {
        modCount[0]++;
      }

      public void profileChanged(Profile profile) {
        modCount[0]++;
      }
    }, project);
  }

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
            return new Result<Boolean>(Boolean.FALSE, fileElement, ProjectRootManager.getInstance(project), myModificationTracker);
          }
        }, false);
        myCachedValues.put(fileElement, cachedValue);
        cachedValue.getValue();
      }
      myDispatcher.getMulticaster().highlightingFinished(fileElement);
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

  public void annotate(final DomElement element, final DomElementAnnotationHolder holder, final Class rootClass) {
    final List<DomElementsAnnotator> list = getAnnotators(rootClass);
    if (list != null) {
      for (DomElementsAnnotator annotator : list) {
        annotator.annotate(element, holder);
      }
    }
  }


  public final void registerDomElementsAnnotator(DomElementsAnnotator annotator, Class<? extends DomElement> aClass) {
    getOrCreateAnnotators(aClass).add(annotator);
  }

  private List<DomElementsAnnotator> getOrCreateAnnotators(final Class aClass) {
    List<DomElementsAnnotator> annotators = getAnnotators(aClass);
    if (annotators == null) {
      myClass2Annotator.put(aClass, annotators = new ArrayList<DomElementsAnnotator>());
    }
    return annotators;
  }

  @Nullable
  private List<DomElementsAnnotator> getAnnotators(final Class aClass) {
    return myClass2Annotator.get(aClass);
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

  public void addHighlightingListener(DomHighlightingListener listener, Disposable parentDisposable) {
    myDispatcher.addListener(listener, parentDisposable);
  }

  public DomHighlightingHelper getHighlightingHelper() {
    return myHighlightingHelper;
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
