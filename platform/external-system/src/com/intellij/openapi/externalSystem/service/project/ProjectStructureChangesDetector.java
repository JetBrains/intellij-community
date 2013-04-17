package com.intellij.openapi.externalSystem.service.project;

import com.intellij.ProjectTopics;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.change.ExternalProjectStructureChange;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.externalSystem.service.project.change.ExternalProjectStructureChangeListener;
import com.intellij.openapi.externalSystem.service.project.change.ProjectStructureChangesModel;
import com.intellij.openapi.externalSystem.service.project.change.user.UserProjectChangesCalculator;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectEntityChangeListener;
import com.intellij.openapi.externalSystem.service.task.ExternalSystemTaskManager;
import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsManager;
import com.intellij.openapi.externalSystem.ui.ExternalProjectStructureTreeModel;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootAdapter;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Alarm;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.externalSystem.service.project.change.AutoImporter;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Detects project structure changes and triggers linked gradle project update.
 * 
 * @author Denis Zhdanov
 * @since 11/3/11 3:57 PM
 */
public class ProjectStructureChangesDetector implements ExternalProjectStructureChangeListener {

  private static final int REFRESH_DELAY_MILLIS = (int)500;

  private final Alarm          myAlarm              = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);
  private final AtomicLong     myStartRefreshTime   = new AtomicLong();
  private final AtomicInteger  myImportCounter      = new AtomicInteger();
  private final AtomicBoolean  myNewChangesDetected = new AtomicBoolean();

  @NotNull private final ProjectStructureChangesModel  myChangesModel;
  @NotNull private final ExternalSystemSettingsManager mySettingsManager;
  @NotNull private final Project                       myProject;
  @NotNull private final UserProjectChangesCalculator  myUserProjectChangesCalculator;
  @NotNull private final AutoImporter                  myAutoImporter;

  public ProjectStructureChangesDetector(@NotNull Project project,
                                         @NotNull ProjectStructureChangesModel model,
                                         @NotNull ExternalSystemSettingsManager manager,
                                         @NotNull UserProjectChangesCalculator calculator,
                                         @NotNull AutoImporter importer)
  {
    myProject = project;
    myChangesModel = model;
    mySettingsManager = manager;
    myUserProjectChangesCalculator = calculator;
    myAutoImporter = importer;
    myChangesModel.addListener(this);
    subscribeToGradleImport(project);
    subscribeToRootChanges(project);
  }

  private void subscribeToGradleImport(@NotNull final Project project) {
    MessageBusConnection connection = project.getMessageBus().connect(project);
    connection.subscribe(ProjectEntityChangeListener.TOPIC, new ProjectEntityChangeListener() {
      @Override
      public void onChangeStart(@NotNull Object entity, @NotNull ProjectSystemId externalSystemId) {
        myImportCounter.incrementAndGet();
      }

      @Override
      public void onChangeEnd(@NotNull Object entity, @NotNull ProjectSystemId externalSystemId) {
        if (myImportCounter.decrementAndGet() <= 0) {

          myUserProjectChangesCalculator.updateCurrentProjectState(project);
          
          // TODO den implement
//          ProjectData externalProject = myChangesModel.getExternalProject(externalSystemId, project);
//          if (externalProject != null) {
//            myChangesModel.update(externalProject, project, true);
//          }

          // There is a possible case that we need to add/remove IJ-specific new nodes because of the IJ project structure changes
          // triggered by gradle.
          rebuildTreeModel(externalSystemId);
        }
      }
    });
  }

  private void subscribeToRootChanges(@NotNull final Project project) {
    project.getMessageBus().connect(project).subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter() {
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        if (myImportCounter.get() <= 0) {
          scheduleUpdate(project);
        }
      }
    });
  }

  private void rebuildTreeModel(@NotNull ProjectSystemId externalSystemId) {
    final ExternalProjectStructureTreeModel treeModel = ExternalSystemUtil.getToolWindowElement(
      ExternalProjectStructureTreeModel.class, myProject, ExternalSystemDataKeys.PROJECT_TREE_MODEL, externalSystemId
    );
    if (treeModel != null) {
      treeModel.rebuild(myAutoImporter.isInProgress());
    }
  }

  @Override
  public void onChanges(@NotNull Project ideProject,
                        @NotNull ProjectSystemId externalSystemId,
                        @NotNull Collection<ExternalProjectStructureChange> oldChanges,
                        @NotNull Collection<ExternalProjectStructureChange> currentChanges)
  {
    myNewChangesDetected.set(true);
  }

  private void scheduleUpdate(@NotNull Project project) {
    for (ExternalSystemManager manager : ExternalSystemManager.EP_NAME.getExtensions()) {
      ProjectSystemId systemId = manager.getSystemId();
      scheduleUpdate(project, systemId);
    }
  }
  
  private void scheduleUpdate(@NotNull Project ideProject, @NotNull ProjectSystemId externalSystemId) {
    if (ApplicationManager.getApplication().isUnitTestMode()
        || StringUtil.isEmpty(mySettingsManager.getSettings(myProject, externalSystemId).getLinkedExternalProjectPath()))
    {
      return;
    }

    myUserProjectChangesCalculator.updateChanges(ideProject);
    
    // We experienced a situation when project root change event has been fired but no actual project structure change has
    // occurred (e.g. compile output directory was modified externally). That's why we perform additional check here in order
    // to ensure that project structure has really been changed.
    //
    // The idea is to check are there any new project structure changes comparing to the gradle project structure used last time.
    // We don't do anything in case no new changes have been detected.
    // TODO den implement
//    ProjectData project = myChangesModel.getExternalProject(externalSystemId, ideProject);
//    if (project != null) {
//      myNewChangesDetected.set(false);
//      myChangesModel.update(project, ideProject, true);
//      if (!myNewChangesDetected.get()) {
//        return;
//      }
//    }

    myStartRefreshTime.set(System.currentTimeMillis() + REFRESH_DELAY_MILLIS);
    myAlarm.cancelAllRequests();
    myAlarm.addRequest(new RefreshRequest(externalSystemId), REFRESH_DELAY_MILLIS);
  }
  
  private class RefreshRequest implements Runnable {
    
    @NotNull private final ProjectSystemId myExternalSystemId;

    RefreshRequest(@NotNull ProjectSystemId id) {
      myExternalSystemId = id;
    }

    @Override
    public void run() {
      if (myProject.isDisposed()) {
        myAlarm.cancelAllRequests();
        return;
      }
      if (!myProject.isInitialized()) {
        return;
      }
      myAlarm.cancelAllRequests();
      final ExternalSystemTaskManager taskManager = ServiceManager.getService(ExternalSystemTaskManager.class);
      if (taskManager != null && taskManager.hasTaskOfTypeInProgress(ExternalSystemTaskType.RESOLVE_PROJECT)) {
        return;
      }

      final long diff = System.currentTimeMillis() - myStartRefreshTime.get();
      if (diff < 0) {
        myAlarm.cancelAllRequests();
        myAlarm.addRequest(this, (int)-diff);
        return;
      }

      UIUtil.invokeAndWaitIfNeeded(new Runnable() {
        @Override
        public void run() {
          if (ModalityState.current() != ModalityState.NON_MODAL) {
            // There is a possible case that user performs intellij project structure modification and 'project settings' dialog
            // is open. We want to perform the refresh when the editing is completely finished then.
            myAlarm.cancelAllRequests();
            myAlarm.addRequest(RefreshRequest.this, REFRESH_DELAY_MILLIS);
            return;
          }

          // There is a possible case that we need to add/remove IJ-specific new nodes because of the IJ project structure changes.
          rebuildTreeModel(myExternalSystemId);
          //GradleUtil.refreshProject(myProject);
        }
      });
    }
  }
}
