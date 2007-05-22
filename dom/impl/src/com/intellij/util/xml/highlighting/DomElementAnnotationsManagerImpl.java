/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.highlighting;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.profile.Profile;
import com.intellij.profile.ProfileChangeAdapter;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.EventDispatcher;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class DomElementAnnotationsManagerImpl extends DomElementAnnotationsManager {
  public static final Object LOCK = new Object();

  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.highlighting.DomElementAnnotationsManagerImpl");
  private static final Key<DomElementsProblemsHolderImpl> DOM_PROBLEM_HOLDER_KEY = Key.create("DomProblemHolder");
  private static final Key<CachedValue<Boolean>> CACHED_VALUE_KEY = Key.create("DomProblemHolderCachedValue");
  private final EventDispatcher<DomHighlightingListener> myDispatcher = EventDispatcher.create(DomHighlightingListener.class);

  private final Map<Class, List<DomElementsAnnotator>> myClass2Annotator = new THashMap<Class, List<DomElementsAnnotator>>();

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

    public List<DomElementProblemDescriptor> getAllProblems(@NotNull DomElementsInspection inspection) {
      return Collections.emptyList();
    }

    public boolean isInspectionCompleted(@NotNull final DomElementsInspection inspectionClass) {
      return false;
    }

  };
  private final DomHighlightingHelperImpl myHighlightingHelper = new DomHighlightingHelperImpl(this);
  private final ModificationTracker myModificationTracker;
  private final ProjectRootManager myProjectRootManager;
  private final CachedValuesManager myCachedValuesManager;
  private long myModificationCount;

  public DomElementAnnotationsManagerImpl(Project project, InspectionProfileManager manager, ProjectRootManager projectRootManager,
                                          PsiManager psiManager) {
    this(project, manager, projectRootManager, psiManager.getCachedValuesManager());
  }
  public DomElementAnnotationsManagerImpl(Project project, final InspectionProfileManager inspectionProfileManager, ProjectRootManager projectRootManager,
                                          final CachedValuesManager cachedValuesManager) {
    myCachedValuesManager = cachedValuesManager;
    myProjectRootManager = projectRootManager;
    myModificationTracker = new ModificationTracker() {
      public long getModificationCount() {
        return myModificationCount;
      }
    };
    final ProfileChangeAdapter profileChangeAdapter = new ProfileChangeAdapter() {
      public void profileActivated(@Nullable NamedScope scope, Profile oldProfile, Profile profile) {
        dropAnnotationsCache();
      }

      public void profileChanged(Profile profile) {
        dropAnnotationsCache();
      }
    };
    inspectionProfileManager.addProfileChangeListener(profileChangeAdapter, project);
    Disposer.register(project, new Disposable() {
      public void dispose() {
        inspectionProfileManager.removeProfileChangeListener(profileChangeAdapter);
      }
    });
  }

  public void dropAnnotationsCache() {
    myModificationCount++;
  }

  public final List<DomElementProblemDescriptor> appendProblems(@NotNull DomFileElement element, @NotNull DomElementAnnotationHolder annotationHolder, Class<? extends DomElementsInspection> inspectionClass) {
    final DomElementAnnotationHolderImpl holderImpl = (DomElementAnnotationHolderImpl)annotationHolder;
    synchronized (LOCK) {
      final DomElementsProblemsHolderImpl holder = _getOrCreateProblemsHolder(element);
      holder.appendProblems(holderImpl, inspectionClass);
    }
    myDispatcher.getMulticaster().highlightingFinished(element);
    return Collections.unmodifiableList(holderImpl);
  }

  private DomElementsProblemsHolderImpl _getOrCreateProblemsHolder(final DomFileElement element) {
    final DomElementsProblemsHolderImpl holder;
    if (isHolderOutdated(element)) {
      holder = new DomElementsProblemsHolderImpl(element);
      element.putUserData(DOM_PROBLEM_HOLDER_KEY, holder);
      final CachedValue<Boolean> cachedValue = myCachedValuesManager.createCachedValue(new CachedValueProvider<Boolean>() {
        public Result<Boolean> compute() {
          return new Result<Boolean>(Boolean.FALSE, element, PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT, myModificationTracker, myProjectRootManager);
        }
      }, false);
      cachedValue.getValue();
      element.putUserData(CACHED_VALUE_KEY, cachedValue);
    }
    else {
      holder = element.getUserData(DOM_PROBLEM_HOLDER_KEY);
      LOG.assertTrue(holder != null);
    }
    return holder;
  }

  public static boolean isHolderUpToDate(DomElement element) {
    synchronized (LOCK) {
      return !isHolderOutdated(element.getRoot());
    }
  }

  public static void outdateProblemHolder(final DomElement element) {
    synchronized (LOCK) {
      element.getRoot().putUserData(CACHED_VALUE_KEY, null);
    }
  }

  private static boolean isHolderOutdated(final DomFileElement element) {
    final CachedValue<Boolean> cachedValue = element.getUserData(CACHED_VALUE_KEY);
    return cachedValue == null || !cachedValue.hasUpToDateValue();
  }

  @NotNull
  public DomElementsProblemsHolder getProblemHolder(DomElement element) {
    if (element == null || !element.isValid()) return EMPTY_PROBLEMS_HOLDER;
    final DomFileElement<DomElement> fileElement = element.getRoot();

    synchronized (LOCK) {
      final DomElementsProblemsHolder readyHolder = fileElement.getUserData(DOM_PROBLEM_HOLDER_KEY);
      return readyHolder == null ? EMPTY_PROBLEMS_HOLDER : readyHolder;
    }
  }

  @NotNull
  public DomElementsProblemsHolder getCachedProblemHolder(DomElement element) {
    return getProblemHolder(element);
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
    return ContainerUtil.createMaybeSingletonList(DomElementsHighlightingUtil.createProblemDescriptors(manager, problemDescriptor));
  }

  public boolean isHighlightingFinished(final DomElement[] domElements) {
    for (final DomElement domElement : domElements) {
      if (getHighlightStatus(domElement) != DomHighlightStatus.INSPECTIONS_FINISHED) {
        return false;
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
  public <T extends DomElement> List<DomElementProblemDescriptor> checkFileElement(@NotNull final DomFileElement<T> domFileElement,
                                                                                   @NotNull final DomElementsInspection<T> inspection) {
    final DomElementsProblemsHolder problemHolder = getProblemHolder(domFileElement);
    if (isHolderUpToDate(domFileElement) && problemHolder.isInspectionCompleted(inspection)) {
      return problemHolder.getAllProblems(inspection);
    }

    final DomElementAnnotationHolder holder = new DomElementAnnotationHolderImpl();
    inspection.checkFileElement(domFileElement, holder);
    return appendProblems(domFileElement, holder, inspection.getClass());
  }

  public List<DomElementsInspection> getSuitableDomInspections(final DomFileElement fileElement, boolean enabledOnly) {
    Class rootType = fileElement.getRootElementClass();
    final InspectionProfile profile = getInspectionProfile(fileElement);
    final List<DomElementsInspection> inspections = new SmartList<DomElementsInspection>();
    for (final InspectionProfileEntry profileEntry : profile.getInspectionTools()) {
      if (!enabledOnly || profile.isToolEnabled(HighlightDisplayKey.find(profileEntry.getShortName()))) {
        ContainerUtil.addIfNotNull(getSuitableInspection(profileEntry, rootType), inspections);
      }
    }
    return inspections;
  }

  protected InspectionProfile getInspectionProfile(final DomFileElement fileElement) {
    return InspectionProjectProfileManager.getInstance(fileElement.getManager().getProject()).getInspectionProfile(fileElement.getFile());
  }

  @Nullable
  private static DomElementsInspection getSuitableInspection(InspectionProfileEntry entry, Class rootType) {
    if (entry instanceof LocalInspectionToolWrapper) {
      return getSuitableInspection(((LocalInspectionToolWrapper)entry).getTool(), rootType);
    }

    if (entry instanceof DomElementsInspection) {
      if (((DomElementsInspection)entry).getDomClasses().contains(rootType)) {
        return (DomElementsInspection) entry;
      }
    }
    return null;
  }

  @Nullable public <T extends DomElement>  DomElementsInspection<T> getMockInspection(DomFileElement<T> root) {
    if (root.getFileDescription().isAutomaticHighlightingEnabled()) {
      return new MockAnnotatingDomInspection<T>(root.getRootElementClass());
    }
    if (getSuitableDomInspections(root, false).isEmpty()) {
      return new MockDomInspection<T>(root.getRootElementClass());
    }

    return null;
  }

  private static boolean areInspectionsFinished(DomElementsProblemsHolderImpl holder, final List<DomElementsInspection> suitableInspections) {
    for (final DomElementsInspection inspection : suitableInspections) {
      if (!holder.isInspectionCompleted(inspection)) {
        return false;
      }
    }
    return true;
  }

  @NotNull
  public DomHighlightStatus getHighlightStatus(final DomElement element) {
    synchronized (LOCK) {
      final DomFileElement<DomElement> root = element.getRoot();
      if (!isHolderOutdated(root)) {
        final DomElementsProblemsHolder holder = getProblemHolder(element);
        if (holder instanceof DomElementsProblemsHolderImpl) {
          DomElementsProblemsHolderImpl holderImpl = (DomElementsProblemsHolderImpl)holder;
          final List<DomElementsInspection> suitableInspections = getSuitableDomInspections(root, true);
          final DomElementsInspection mockInspection = getMockInspection(root);
          final boolean annotatorsFinished = mockInspection == null || holderImpl.isInspectionCompleted(mockInspection);
          final boolean inspectionsFinished = areInspectionsFinished(holderImpl, suitableInspections);
          if (annotatorsFinished) {
            if (suitableInspections.isEmpty() || inspectionsFinished) return DomHighlightStatus.INSPECTIONS_FINISHED;
            return DomHighlightStatus.ANNOTATORS_FINISHED;
          }
        }
      }
      return DomHighlightStatus.NONE;
    }

  }
}
