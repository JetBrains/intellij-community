// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.projectRoot.daemon;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.util.Alarm;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProjectStructureDaemonAnalyzer implements Disposable {
  private static final Logger LOG = Logger.getInstance(ProjectStructureDaemonAnalyzer.class);
  private final Map<ProjectStructureElement, ProjectStructureProblemsHolderImpl> myProblemHolders = new HashMap<>();
  private final MultiValuesMap<ProjectStructureElement, ProjectStructureElementUsage> mySourceElement2Usages = new MultiValuesMap<>();
  private final MultiValuesMap<ProjectStructureElement, ProjectStructureElementUsage> myContainingElement2Usages = new MultiValuesMap<>();
  private final Set<ProjectStructureElement> myElementWithNotCalculatedUsages = new HashSet<>();
  private final Set<ProjectStructureElement> myElementsToShowWarningIfUnused = new HashSet<>();
  private final Map<ProjectStructureElement, ProjectStructureProblemDescription> myWarningsAboutUnused = new HashMap<>();
  private final SimpleMergingQueue<Runnable> myAnalyzerQueue;
  private final SimpleMergingQueue<Runnable> myResultsUpdateQueue;
  private final EventDispatcher<ProjectStructureDaemonAnalyzerListener> myDispatcher = EventDispatcher.create(ProjectStructureDaemonAnalyzerListener.class);
  private final AtomicBoolean myStopped = new AtomicBoolean(false);
  private final ProjectConfigurationProblems myProjectConfigurationProblems;

  public ProjectStructureDaemonAnalyzer(@NotNull StructureConfigurableContext context) {
    Disposer.register(context, this);
    myProjectConfigurationProblems = new ProjectConfigurationProblems(this, context);

    myAnalyzerQueue = new SimpleMergingQueue<>("Project Structure Daemon Analyzer", 300, false, Alarm.ThreadToUse.POOLED_THREAD, this);
    myResultsUpdateQueue = new SimpleMergingQueue<>("Project Structure Analysis Results Updater", 300, false, Alarm.ThreadToUse.SWING_THREAD, this);
  }

  private void doUpdate(@NotNull ProjectStructureElement element) {
    if (myStopped.get()) return;

    doCheck(element);
    doCollectUsages(element);
  }

  private void doCheck(@NotNull ProjectStructureElement element) {
    final ProjectStructureProblemsHolderImpl problemsHolder = new ProjectStructureProblemsHolderImpl();
    ReadAction.run(() -> {
      if (myStopped.get()) return;

      if (LOG.isDebugEnabled()) {
        LOG.debug("checking " + element);
      }
      ProjectStructureValidator.check(element, problemsHolder);
    });
    myResultsUpdateQueue.queue(new ProblemsComputedUpdate(element, problemsHolder));
  }

  private void doCollectUsages(@NotNull ProjectStructureElement element) {
    final List<ProjectStructureElementUsage> usages = ReadAction.compute(() -> {
      if (myStopped.get()) return null;

      if (LOG.isDebugEnabled()) {
        LOG.debug("collecting usages in " + element);
      }
      return getUsagesInElement(element);
    });
    if (usages != null) {
      myResultsUpdateQueue.queue(new UsagesCollectedUpdate(element, usages));
    }
  }

  private static List<ProjectStructureElementUsage> getUsagesInElement(@NotNull ProjectStructureElement element) {
    return ProjectStructureValidator.getUsagesInElement(element);
  }

  private void updateUsages(@NotNull ProjectStructureElement element, @NotNull List<? extends ProjectStructureElementUsage> usages) {
    removeUsagesInElement(element);
    for (ProjectStructureElementUsage usage : usages) {
      addUsage(usage);
    }
    myElementWithNotCalculatedUsages.remove(element);
    myResultsUpdateQueue.queue(new ReportUnusedElementsUpdate());
  }

  public void queueUpdate(@NotNull ProjectStructureElement element) {
    queueUpdates(List.of(element));
  }

  public void queueUpdates(@NotNull Collection<? extends ProjectStructureElement> elements) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("start checking and collecting usages for " + elements);
    }
    myElementWithNotCalculatedUsages.addAll(elements);
    for (ProjectStructureElement element : elements) {
      if (element.shouldShowWarningIfUnused()) {
        myElementsToShowWarningIfUnused.add(element);
      }
    }
    myAnalyzerQueue.queue(ContainerUtil.map(elements, AnalyzeElementUpdate::new));
  }

  public void removeElement(@NotNull ProjectStructureElement element) {
    removeElements(Collections.singletonList(element));
  }

  public void removeElements(@NotNull List<? extends ProjectStructureElement> elements) {
    for (ProjectStructureElement element : elements) {
      myElementWithNotCalculatedUsages.remove(element);
      myElementsToShowWarningIfUnused.remove(element);
      myWarningsAboutUnused.remove(element);
      myProblemHolders.remove(element);
      final Collection<ProjectStructureElementUsage> usages = mySourceElement2Usages.removeAll(element);
      if (usages != null) {
        for (ProjectStructureElementUsage usage : usages) {
          myProblemHolders.remove(usage.getContainingElement());
        }
      }
      removeUsagesInElement(element);
      myDispatcher.getMulticaster().problemsChanged(element);
    }
    myResultsUpdateQueue.queue(new ReportUnusedElementsUpdate());
  }


  private void reportUnusedElements() {
    if (!myElementWithNotCalculatedUsages.isEmpty()) return;

    for (ProjectStructureElement element : myElementsToShowWarningIfUnused) {
      final ProjectStructureProblemDescription warning;
      final Collection<ProjectStructureElementUsage> usages = mySourceElement2Usages.get(element);
      if (usages == null || usages.isEmpty()) {
        warning = element.createUnusedElementWarning();
      }
      else {
        warning = null;
      }

      final ProjectStructureProblemDescription old = myWarningsAboutUnused.put(element, warning);
      ProjectStructureProblemsHolderImpl holder = myProblemHolders.get(element);
      if (holder == null) {
        holder = new ProjectStructureProblemsHolderImpl();
        myProblemHolders.put(element, holder);
      }
      if (old != null) {
        holder.removeProblem(old);
      }
      if (warning != null) {
        holder.registerProblem(warning);
      }
      if (old != null || warning != null) {
        myDispatcher.getMulticaster().problemsChanged(element);
      }
    }
  }

  private void removeUsagesInElement(ProjectStructureElement element) {
    final Collection<ProjectStructureElementUsage> usages = myContainingElement2Usages.removeAll(element);
    if (usages != null) {
      for (ProjectStructureElementUsage usage : usages) {
        mySourceElement2Usages.remove(usage.getSourceElement(), usage);
      }
    }
  }

  private void addUsage(@NotNull ProjectStructureElementUsage usage) {
    mySourceElement2Usages.put(usage.getSourceElement(), usage);
    myContainingElement2Usages.put(usage.getContainingElement(), usage);
  }

  public void stop() {
    LOG.debug("analyzer stopped");
    myStopped.set(true);
    clearCaches();
    myAnalyzerQueue.stop();
    myResultsUpdateQueue.stop();
  }

  public void clearCaches() {
    LOG.debug("clear caches");
    myProblemHolders.clear();
  }

  public void queueUpdateForAllElementsWithErrors() {
    List<ProjectStructureElement> toUpdate = new ArrayList<>();
    for (Map.Entry<ProjectStructureElement, ProjectStructureProblemsHolderImpl> entry : myProblemHolders.entrySet()) {
      if (entry.getValue().containsProblems()) {
        toUpdate.add(entry.getKey());
      }
    }
    myProblemHolders.clear();
    LOG.debug("Adding to queue updates for " + toUpdate.size() + " problematic elements");

    queueUpdates(toUpdate);
  }

  @Override
  public void dispose() {
    myStopped.set(true);
    myAnalyzerQueue.stop();
    myResultsUpdateQueue.stop();
  }

  public @Nullable ProjectStructureProblemsHolderImpl getProblemsHolder(@NotNull ProjectStructureElement element) {
    return myProblemHolders.get(element);
  }

  public Collection<ProjectStructureElementUsage> getUsages(@NotNull ProjectStructureElement selected) {
    ProjectStructureElement[] elements = myElementWithNotCalculatedUsages.toArray(new ProjectStructureElement[0]);
    for (ProjectStructureElement element : elements) {
      updateUsages(element, getUsagesInElement(element));
    }
    final Collection<ProjectStructureElementUsage> usages = mySourceElement2Usages.get(selected);
    return usages != null ? usages : Collections.emptyList();
  }

  public void addListener(@NotNull ProjectStructureDaemonAnalyzerListener listener) {
    LOG.debug("listener added " + listener);
    myDispatcher.addListener(listener);
  }

  public void reset() {
    LOG.debug("analyzer started");
    myAnalyzerQueue.start();
    myResultsUpdateQueue.start();
    myAnalyzerQueue.queue(new Update("reset") {
      @Override
      public void run() {
        myStopped.set(false);
      }
    });
  }

  public void clear() {
    myWarningsAboutUnused.clear();
    myElementsToShowWarningIfUnused.clear();
    mySourceElement2Usages.clear();
    myContainingElement2Usages.clear();
    myElementWithNotCalculatedUsages.clear();
    myProjectConfigurationProblems.clearProblems();
  }

  private final class AnalyzeElementUpdate implements Runnable {
    private final @NotNull ProjectStructureElement myElement;

    AnalyzeElementUpdate(@NotNull ProjectStructureElement element) {
      myElement = element;
    }

    @Override
    public void run() {
      try {
        doUpdate(myElement);
      }
      catch (Throwable t) {
        LOG.error(t);
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof AnalyzeElementUpdate update)) return false;

      return myElement.equals(update.myElement);
    }

    @Override
    public int hashCode() {
      return myElement.hashCode();
    }
  }

  private final class UsagesCollectedUpdate implements Runnable {
    private final @NotNull ProjectStructureElement myElement;
    private final @NotNull List<? extends ProjectStructureElementUsage> myUsages;

    UsagesCollectedUpdate(@NotNull ProjectStructureElement element, @NotNull List<? extends ProjectStructureElementUsage> usages) {
      myElement = element;
      myUsages = usages;
    }

    @Override
    public void run() {
      if (myStopped.get()) return;

      if (LOG.isDebugEnabled()) {
        LOG.debug("updating usages for " + myElement);
      }
      updateUsages(myElement, myUsages);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof UsagesCollectedUpdate other)) return false;

      return myElement.equals(other.myElement);
    }

    @Override
    public int hashCode() {
      return myElement.hashCode() + 1;
    }
  }

  private final class ProblemsComputedUpdate implements Runnable {
    private final @NotNull ProjectStructureElement myElement;
    private final @NotNull ProjectStructureProblemsHolderImpl myProblemsHolder;

    ProblemsComputedUpdate(@NotNull ProjectStructureElement element, @NotNull ProjectStructureProblemsHolderImpl problemsHolder) {
      myElement = element;
      myProblemsHolder = problemsHolder;
    }

    @Override
    public void run() {
      if (myStopped.get()) return;

      if (LOG.isDebugEnabled()) {
        LOG.debug("updating problems for " + myElement);
      }
      final ProjectStructureProblemDescription warning = myWarningsAboutUnused.get(myElement);
      if (warning != null) {
        myProblemsHolder.registerProblem(warning);
      }
      myProblemHolders.put(myElement, myProblemsHolder);
      myDispatcher.getMulticaster().problemsChanged(myElement);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof ProblemsComputedUpdate update)) return false;

      return myElement.equals(update.myElement);
    }

    @Override
    public int hashCode() {
      return myElement.hashCode() + 2;
    }
  }

  private final class ReportUnusedElementsUpdate implements Runnable {
    @Override
    public void run() {
      reportUnusedElements();
    }

    @Override
    public int hashCode() {
      return 3;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof ReportUnusedElementsUpdate;
    }
  }
}
