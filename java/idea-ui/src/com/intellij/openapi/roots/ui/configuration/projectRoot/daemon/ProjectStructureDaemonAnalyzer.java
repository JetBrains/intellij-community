package com.intellij.openapi.roots.ui.configuration.projectRoot.daemon;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author nik
 */
public class ProjectStructureDaemonAnalyzer implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.ui.configuration.projectRoot.validation.ProjectStructureDaemonAnalyzer");
  private final Map<ProjectStructureElement, ProjectStructureProblemsHolderImpl> myProblemHolders = new HashMap<ProjectStructureElement, ProjectStructureProblemsHolderImpl>();
  private final MultiValuesMap<ProjectStructureElement, ProjectStructureElementUsage> mySourceElement2Usages = new MultiValuesMap<ProjectStructureElement, ProjectStructureElementUsage>();
  private final MultiValuesMap<ProjectStructureElement, ProjectStructureElementUsage> myContainingElement2Usages = new MultiValuesMap<ProjectStructureElement, ProjectStructureElementUsage>();
  private final Set<ProjectStructureElement> myElementWithNotCalculatedUsages = new HashSet<ProjectStructureElement>();
  private final MergingUpdateQueue myAnalyzerQueue;
  private final EventDispatcher<ProjectStructureDaemonAnalyzerListener> myDispatcher = EventDispatcher.create(ProjectStructureDaemonAnalyzerListener.class);
  private final AtomicBoolean myStopped = new AtomicBoolean(false);

  public ProjectStructureDaemonAnalyzer(StructureConfigurableContext context) {
    Disposer.register(context, this);
    myAnalyzerQueue = new MergingUpdateQueue("Project Structure Daemon Analyzer", 300, false, null, this, null, false);
  }

  private void doUpdate(final ProjectStructureElement element, final boolean check, final boolean collectUsages) {
    if (myStopped.get()) return;

    if (check) {
      doCheck(element);
    }
    if (collectUsages) {
      doCollectUsages(element);
    }
  }

  private void doCheck(final ProjectStructureElement element) {
    final ProjectStructureProblemsHolderImpl problemsHolder = new ProjectStructureProblemsHolderImpl();
    new ReadAction() {
      protected void run(final Result result) {
        if (myStopped.get()) return;

        if (LOG.isDebugEnabled()) {
          LOG.debug("checking " + element);
        }
        element.check(problemsHolder);
      }
    }.execute();
    invokeLater(new Runnable() {
      public void run() {
        if (myStopped.get()) return;

        if (LOG.isDebugEnabled()) {
          LOG.debug("updating problems for " + element);
        }
        myProblemHolders.put(element, problemsHolder);
        myDispatcher.getMulticaster().problemsChanged(element);
      }
    });
  }

  private void doCollectUsages(final ProjectStructureElement element) {
    final List<ProjectStructureElementUsage> usages = new ReadAction<List<ProjectStructureElementUsage>>() {
      protected void run(final Result<List<ProjectStructureElementUsage>> result) {
        if (myStopped.get()) return;

        if (LOG.isDebugEnabled()) {
          LOG.debug("collecting usages in " + element);
        }
        result.setResult(element.getUsagesInElement());
      }
    }.execute().getResultObject();

    invokeLater(new Runnable() {
      public void run() {
        if (myStopped.get()) return;

        if (LOG.isDebugEnabled()) {
          LOG.debug("updating usages for " + element);
        }
        updateUsages(element, usages);
        myDispatcher.getMulticaster().usagesCollected(element);
      }
    });
  }

  private void updateUsages(ProjectStructureElement element, List<ProjectStructureElementUsage> usages) {
    removeUsagesInElement(element);
    for (ProjectStructureElementUsage usage : usages) {
      addUsage(usage);
    }
    myElementWithNotCalculatedUsages.remove(element);
  }

  private static void invokeLater(Runnable runnable) {
    SwingUtilities.invokeLater(runnable);
  }

  public void queueUpdate(@NotNull final ProjectStructureElement element) {
    queueUpdate(element, true, true);
  }

  public void queueUpdate(@NotNull final ProjectStructureElement element, final boolean check, final boolean collectUsages) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("start " + (check ? "checking " : "") + (collectUsages ? "collecting usages " : "") + "for " + element);
    }
    if (collectUsages) {
      myElementWithNotCalculatedUsages.add(element);
    }
    myAnalyzerQueue.queue(new AnalyzeElementUpdate(element, check, collectUsages));
  }

  public void removeElement(ProjectStructureElement element) {
    myElementWithNotCalculatedUsages.remove(element);
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

  public boolean isUnused(ProjectStructureElement element) {
    if (!element.highlightIfUnused()) {
      return false;
    }
    if (!myElementWithNotCalculatedUsages.isEmpty()) {
      return false;
    }
    final Collection<ProjectStructureElementUsage> usages = mySourceElement2Usages.get(element);
    return usages == null || usages.isEmpty();
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
    myAnalyzerQueue.cancelAllUpdates();
    clearCaches();
    myAnalyzerQueue.deactivate();
  }

  public void clearCaches() {
    LOG.debug("clear caches");
    myProblemHolders.clear();
  }

  public void clearAllProblems() {
    myProblemHolders.clear();
    myDispatcher.getMulticaster().allProblemsChanged();
  }

  public void dispose() {
    myStopped.set(true);
    myAnalyzerQueue.cancelAllUpdates();
  }

  @Nullable
  public ProjectStructureProblemsHolderImpl getProblemsHolder(ProjectStructureElement element) {
    return myProblemHolders.get(element);
  }

  public Collection<ProjectStructureElementUsage> getUsages(ProjectStructureElement selected) {
    ProjectStructureElement[] elements = myElementWithNotCalculatedUsages.toArray(new ProjectStructureElement[myElementWithNotCalculatedUsages.size()]);
    for (ProjectStructureElement element : elements) {
      updateUsages(element, element.getUsagesInElement());
    }
    final Collection<ProjectStructureElementUsage> usages = mySourceElement2Usages.get(selected);
    return usages != null ? usages : Collections.<ProjectStructureElementUsage>emptyList();
  }

  public void addListener(ProjectStructureDaemonAnalyzerListener listener) {
    LOG.debug("listener added " + listener);
    myDispatcher.addListener(listener);
  }

  public void reset() {
    LOG.debug("analyzer started");
    myAnalyzerQueue.activate();
    myAnalyzerQueue.queue(new Update("reset") {
      public void run() {
        myStopped.set(false);
      }
    });
  }

  public void clear() {
    mySourceElement2Usages.clear();
    myContainingElement2Usages.clear();
    myElementWithNotCalculatedUsages.clear();
  }

  private class AnalyzeElementUpdate extends Update {
    private final ProjectStructureElement myElement;
    private final boolean myCheck;
    private final boolean myCollectUsages;
    private final Object[] myEqualityObjects;

    public AnalyzeElementUpdate(ProjectStructureElement element, boolean check, boolean collectUsages) {
      super(element);
      myElement = element;
      myCheck = check;
      myCollectUsages = collectUsages;
      myEqualityObjects = new Object[]{myElement, myCheck, myCollectUsages};
    }

    @Override
    public boolean canEat(Update update) {
      if (!(update instanceof AnalyzeElementUpdate)) return false;
      final AnalyzeElementUpdate other = (AnalyzeElementUpdate)update;
      return myElement.equals(other.myElement) && (!other.myCheck || myCheck) && (!other.myCollectUsages || myCollectUsages);
    }

    @Override
    public Object[] getEqualityObjects() {
      return myEqualityObjects;
    }

    public void run() {
      try {
        doUpdate(myElement, myCheck, myCollectUsages);
      }
      catch (Throwable t) {
        LOG.error(t);
      }
    }
  }
}
