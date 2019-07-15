// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.configurationStore.StorageUtilKt;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.*;
import com.intellij.openapi.wm.impl.welcomeScreen.RecentProjectPanel;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import com.intellij.platform.PlatformProjectOpenProcessor;
import com.intellij.project.ProjectKt;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Used directly by IntelliJ IDEA.
 *
 * @see RecentDirectoryProjectsManager base class primary for minor IDEs on IntelliJ Platform
 */
@State(name = "RecentProjectsManager", storages = @Storage(value = "recentProjects.xml", roamingType = RoamingType.DISABLED))
public class RecentProjectsManagerBase extends RecentProjectsManager implements PersistentStateComponent<RecentProjectManagerState>,
                                                                                ModificationTracker {
  private static final int MAX_PROJECTS_IN_MAIN_MENU = 6;

  private final AtomicLong myModCounter = new AtomicLong();

  private final RecentProjectIconHelper myProjectIconHelper = new RecentProjectIconHelper();

  private final Set<String> myNamesToResolve = new THashSet<>(MAX_PROJECTS_IN_MAIN_MENU);
  private final Map<String, String> myNameCache = Collections.synchronizedMap(new THashMap<>());

  private final SingleAlarm myNamesResolver = new SingleAlarm(() -> {
    final Set<String> paths;
    synchronized (myNamesToResolve) {
      paths = new THashSet<>(myNamesToResolve);
      myNamesToResolve.clear();
    }
    for (String p : paths) {
      myNameCache.put(p, readProjectName(p));
    }
  }, 50, Alarm.ThreadToUse.POOLED_THREAD, ApplicationManager.getApplication());

  public static RecentProjectsManagerBase getInstanceEx() {
    return (RecentProjectsManagerBase)RecentProjectsManager.getInstance();
  }

  private final Object myStateLock = new Object();
  private RecentProjectManagerState myState = new RecentProjectManagerState();

  @SuppressWarnings("deprecation")
  @Override
  public RecentProjectManagerState getState() {
    synchronized (myStateLock) {
      myState.getRecentPaths().clear();
      myState.getRecentPaths().addAll(ContainerUtil.reverse(new ArrayList<>(myState.getAdditionalInfo().keySet())));

      if (myState.getPid() == null) {
        //todo[kb] uncomment when we will fix JRE-251 The pid is needed for 3rd parties like Toolbox App to show the project is open now
        myState.setPid(null);//OSProcessUtil.getApplicationPid();
      }
      return myState;
    }
  }

  @Nullable
  public RecentProjectMetaInfo getProjectMetaInfo(@NotNull Path file) {
    synchronized (myStateLock) {
      return myState.getAdditionalInfo().get(FileUtil.toSystemIndependentName(file.toString()));
    }
  }

  @Override
  public void loadState(@NotNull RecentProjectManagerState state) {
    synchronized (myStateLock) {
      myState = state;
      myState.setPid(null);

      @SuppressWarnings("deprecation")
      List<String> openPaths = myState.getOpenPaths();
      if (!openPaths.isEmpty()) {
        migrateOpenPaths(openPaths);
      }
    }
  }

  // reorder according to openPaths order and mark as opened
  private void migrateOpenPaths(@NotNull List<String> openPaths) {
    Map<String, RecentProjectMetaInfo> oldInfoMap = new THashMap<>();
    for (String path : openPaths) {
      RecentProjectMetaInfo info = myState.getAdditionalInfo().remove(path);
      if (info != null) {
        oldInfoMap.put(path, info);
      }
    }

    for (String path : ContainerUtil.reverse(openPaths)) {
      RecentProjectMetaInfo info = oldInfoMap.get(path);
      if (info == null) {
        info = new RecentProjectMetaInfo();
      }
      info.setOpened(true);
      myState.getAdditionalInfo().put(path, info);
    }

    openPaths.clear();
    myModCounter.incrementAndGet();
  }

  @Override
  public void removePath(@Nullable @SystemIndependent String path) {
    if (path == null) {
      return;
    }

    synchronized (myStateLock) {
      myState.getAdditionalInfo().remove(path);
      for (ProjectGroup group : myState.getGroups()) {
        if (group.removeProject(path)) {
          myModCounter.incrementAndGet();
        }
      }
    }
  }

  @Override
  public boolean hasPath(@SystemIndependent String path) {
    synchronized (myStateLock) {
      return myState.getAdditionalInfo().containsKey(path);
    }
  }

  /**
   * @return a path pointing to a directory where the last project was created or null if not available
   */
  @Override
  @Nullable
  @SystemIndependent
  public String getLastProjectCreationLocation() {
    synchronized (myStateLock) {
      return myState.getLastProjectLocation();
    }
  }

  @Override
  public void setLastProjectCreationLocation(@Nullable @SystemIndependent String value) {
    String newValue = PathUtil.toSystemIndependentName(StringUtil.nullize(value, true));
    synchronized (myStateLock) {
      myState.setLastProjectLocation(newValue);
    }
  }

  @Override
  public final void updateLastProjectPath() {
    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    synchronized (myStateLock) {
      for (RecentProjectMetaInfo info : myState.getAdditionalInfo().values()) {
        info.setOpened(false);
      }

      for (Project project : openProjects) {
        String path = getProjectPath(project);
        RecentProjectMetaInfo info = path == null ? null : myState.getAdditionalInfo().get(path);
        if (info != null) {
          info.setOpened(true);
          info.setProjectOpenTimestamp(System.currentTimeMillis());
          info.setDisplayName(getProjectDisplayName(project));
        }
      }

      myState.validateRecentProjects(myModCounter);
    }
  }

  @Nullable
  protected String getProjectDisplayName(@NotNull Project project) {
    return null;
  }

  @Nullable
  public final Icon getProjectIcon(@NotNull @SystemIndependent String path, boolean isDark) {
    return myProjectIconHelper.getProjectIcon(path, isDark);
  }

  public final Icon getProjectOrAppIcon(@SystemIndependent @NotNull String path) {
    return myProjectIconHelper.getProjectOrAppIcon(path);
  }

  @NotNull
  private Set<String> getDuplicateProjectNames(@NotNull Set<String> openedPaths, @NotNull Set<String> recentPaths) {
    Set<String> names = new THashSet<>();
    Set<String> duplicates = new THashSet<>();
    for (String path : ContainerUtil.union(openedPaths, recentPaths)) {
      String name = getProjectName(path);
      if (!names.add(name)) {
        duplicates.add(name);
      }
    }
    return duplicates;
  }

  @Override
  @NotNull
  public AnAction[] getRecentProjectsActions(boolean forMainMenu) {
    return getRecentProjectsActions(forMainMenu, false);
  }

  @Override
  @NotNull
  public AnAction[] getRecentProjectsActions(boolean forMainMenu, boolean useGroups) {
    Set<String> paths;
    synchronized (myStateLock) {
      myState.validateRecentProjects(myModCounter);
      paths = new LinkedHashSet<>(ContainerUtil.reverse(new ArrayList<>(myState.getAdditionalInfo().keySet())));
    }

    Set<String> openedPaths = new THashSet<>();
    for (Project openProject : ProjectManager.getInstance().getOpenProjects()) {
      ContainerUtil.addIfNotNull(openedPaths, getProjectPath(openProject));
    }

    List<AnAction> actions = new SmartList<>();
    Set<String> duplicates = getDuplicateProjectNames(openedPaths, paths);
    if (useGroups) {
      final List<ProjectGroup> groups;
      synchronized (myStateLock) {
        groups = new ArrayList<>(myState.getGroups());
      }
      final List<String> projectPaths = new ArrayList<>(paths);
      groups.sort(new Comparator<ProjectGroup>() {
        @Override
        public int compare(ProjectGroup o1, ProjectGroup o2) {
          int ind1 = getGroupIndex(o1);
          int ind2 = getGroupIndex(o2);
          return ind1 == ind2 ? StringUtil.naturalCompare(o1.getName(), o2.getName()) : ind1 - ind2;
        }

        private int getGroupIndex(ProjectGroup group) {
          int index = Integer.MAX_VALUE;
          for (String path : group.getProjects()) {
            final int i = projectPaths.indexOf(path);
            if (i >= 0 && index > i) {
              index = i;
            }
          }
          return index;
        }
      });

      for (ProjectGroup group : groups) {
        paths.removeAll(group.getProjects());
      }

      for (ProjectGroup group : groups) {
        final List<AnAction> children = new ArrayList<>();
        for (String path : group.getProjects()) {
          children.add(createOpenAction(path, duplicates));
          if (forMainMenu && children.size() >= MAX_PROJECTS_IN_MAIN_MENU) {
            break;
          }
        }
        actions.add(new ProjectGroupActionGroup(group, children));
        if (group.isExpanded()) {
          actions.addAll(children);
        }
      }
    }

    for (String path : paths) {
      actions.add(createOpenAction(path, duplicates));
    }

    if (actions.isEmpty()) {
      return AnAction.EMPTY_ARRAY;
    }

    return actions.toArray(AnAction.EMPTY_ARRAY);
  }

  // for Rider
  @NotNull
  protected AnAction createOpenAction(@NotNull @SystemIndependent String path, @NotNull Set<String> duplicates) {
    String projectName = getProjectName(path);
    String displayName;
    synchronized (myStateLock) {
      RecentProjectMetaInfo info = myState.getAdditionalInfo().get(path);
      displayName = info == null ? null : info.getDisplayName();
    }
    if (StringUtil.isEmptyOrSpaces(displayName)) {
      displayName = duplicates.contains(projectName) ? FileUtil.toSystemDependentName(path) : projectName;
    }

    // It's better don't to remove non-existent projects. Sometimes projects stored
    // on USB-sticks or flash-cards, and it will be nice to have them in the list
    // when USB device or SD-card is mounted
    return new ReopenProjectAction(path, projectName, displayName);
  }

  private void markPathRecent(@NotNull @SystemIndependent String path, @NotNull Project project) {
    synchronized (myStateLock) {
      for (ProjectGroup group : myState.getGroups()) {
        if (group.markProjectFirst(path)) {
          myModCounter.incrementAndGet();
          break;
        }
      }

      // remove instead of get to re-order
      RecentProjectMetaInfo info = myState.getAdditionalInfo().remove(path);
      if (info == null) {
        info = new RecentProjectMetaInfo();
      }
      myState.getAdditionalInfo().put(path, info);
      myModCounter.incrementAndGet();

      ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();
      info.setDisplayName(getProjectDisplayName(project));
      info.setProjectWorkspaceId(ProjectKt.getStateStore(project).getProjectWorkspaceId());
      info.setFrame(ProjectFrameBounds.getInstance(project).getState());
      info.setBuild(appInfo.getBuild().asString());
      info.setProductionCode(appInfo.getBuild().getProductCode());
      info.setEap(appInfo.isEAP());
      info.setBinFolder(FileUtilRt.toSystemIndependentName(PathManager.getBinPath()));
      info.setProjectOpenTimestamp(System.currentTimeMillis());
      info.setBuildTimestamp(appInfo.getBuildDate().getTimeInMillis());
      info.setMetadata(getRecentProjectMetadata(path, project));
    }
  }

  @SuppressWarnings("unused")
  @Nullable
  protected String getRecentProjectMetadata(@SystemIndependent String path, @NotNull Project project) {
    return null;
  }

  @Nullable
  @SystemIndependent
  protected String getProjectPath(@NotNull Project project) {
    return PathUtil.toSystemIndependentName(project.getPresentableUrl());
  }

  @Nullable
  public Project doOpenProject(@NotNull @SystemIndependent String projectPath, @NotNull OpenProjectTask openProjectOptions) {
    return doOpenProject(Paths.get(projectPath), openProjectOptions);
  }

  @Nullable
  public Project doOpenProject(@NotNull Path projectFile, @NotNull OpenProjectTask openProjectOptions) {
    Project existing = ProjectUtil.findAndFocusExistingProjectForPath(projectFile);
    if (existing != null) {
      return existing;
    }

    if (ProjectUtil.isValidProjectPath(projectFile)) {
      return PlatformProjectOpenProcessor.openExistingProject(projectFile, projectFile, openProjectOptions, null);
    }
    else {
      // If .idea is missing in the recent project's dir; this might mean, for instance, that 'git clean' was called.
      // Reopening such a project should be similar to opening the dir first time (and trying to import known project formats)
      // IDEA-144453 IDEA rejects opening recent project if there are no .idea subfolder
      // CPP-12106 Auto-load CMakeLists.txt on opening from Recent projects when .idea and cmake-build-debug were deleted
      return ProjectUtil.openOrImport(projectFile, openProjectOptions);
    }
  }

  static final class MyProjectListener implements ProjectManagerListener {
    private final RecentProjectsManagerBase manager = getInstanceEx();

    @Override
    public void projectOpened(@NotNull Project project) {
      String path = manager.getProjectPath(project);
      if (path != null) {
        manager.markPathRecent(path, project);
      }
      manager.updateLastProjectPath();
      updateSystemDockMenu();
    }

    private static void updateSystemDockMenu() {
      if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
        SystemDock.updateMenu();
      }
    }

    @Override
    public void projectClosing(@NotNull Project project) {
      String path = manager.getProjectPath(project);
      if (path == null) {
        return;
      }

      if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
        manager.updateProjectInfo(project, (WindowManagerImpl)WindowManager.getInstance());
      }
      manager.myNameCache.put(path, project.getName());
    }

    @Override
    public void projectClosed(@NotNull Project project) {
      Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
      if (openProjects.length > 0) {
        Project openProject = openProjects[openProjects.length - 1];
        String path = manager.getProjectPath(openProject);
        if (path != null) {
          manager.markPathRecent(path, openProject);
        }
      }
      updateSystemDockMenu();
    }
  }

  @NotNull
  public String getProjectName(@NotNull @SystemIndependent String path) {
    String cached = myNameCache.get(path);
    if (cached != null) {
      return cached;
    }

    myNamesResolver.cancel();
    synchronized (myNamesToResolve) {
      myNamesToResolve.add(path);
    }
    myNamesResolver.request();

    String name = PathUtilRt.getFileName(path);
    return path.endsWith(".ipr") ? FileUtilRt.getNameWithoutExtension(name) : name;
  }

  private static String readProjectName(@NotNull String path) {
    if (!RecentProjectPanel.isFileSystemPath(path))
      return path;

    final Path file = Paths.get(path);
    //noinspection SSBasedInspection
    if (!Files.isDirectory(file)) {
      return FileUtilRt.getNameWithoutExtension(file.getFileName().toString());
    }

    final Path nameFile = file.resolve(Project.DIRECTORY_STORE_FOLDER).resolve(ProjectImpl.NAME_FILE);
    try {
      String result = StorageUtilKt.readProjectNameFile(nameFile);
      if (result != null) {
        return result;
      }
    }
    catch (NoSuchFileException ignore) {
      // ignore not found
    }
    catch (IOException ignored) {
    }
    return file.getFileName().toString();
  }

  @Override
  public boolean willReopenProjectOnStart() {
    if (!GeneralSettings.getInstance().isReopenLastProject()) {
      return false;
    }

    synchronized (myStateLock) {
      for (RecentProjectMetaInfo info : myState.getAdditionalInfo().values()) {
        if (info.getOpened()) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public void reopenLastProjectsOnStart() {
    if (!GeneralSettings.getInstance().isReopenLastProject()) {
      return;
    }

    List<Map.Entry<String, RecentProjectMetaInfo>> openPaths = getLastOpenedProjects();
    boolean someProjectWasOpened = false;
    for (Map.Entry<String, RecentProjectMetaInfo> it : openPaths) {
      // https://youtrack.jetbrains.com/issue/IDEA-166321
      OpenProjectTask options = new OpenProjectTask(/* forceOpenInNewFrame = */ true, /* projectToClose = */ null, it.getValue().getFrame(), /* projectWorkspaceId = */ it.getValue().getProjectWorkspaceId());
      options.setShowWelcomeScreenIfNoProjectOpened(false);
      options.setSendFrameBack(someProjectWasOpened);
      Project project = doOpenProject(it.getKey(), options);
      if (!someProjectWasOpened) {
        someProjectWasOpened = project != null;
      }
    }

    if (!someProjectWasOpened) {
      WelcomeFrame.showIfNoProjectOpened();
    }
  }

  @NotNull
  protected final List<Map.Entry<String, RecentProjectMetaInfo>> getLastOpenedProjects() {
    synchronized (myStateLock) {
      return ContainerUtil.reverse(ContainerUtil.findAll(myState.getAdditionalInfo().entrySet(), it -> it.getValue().getOpened()));
    }
  }

  @Override
  @NotNull
  public List<ProjectGroup> getGroups() {
    synchronized (myStateLock) {
      return Collections.unmodifiableList(myState.getGroups());
    }
  }

  @Override
  public void addGroup(@NotNull ProjectGroup group) {
    synchronized (myStateLock) {
      if (!myState.getGroups().contains(group)) {
        myState.getGroups().add(group);
      }
    }
  }

  @Override
  public void removeGroup(@NotNull ProjectGroup group) {
    synchronized (myStateLock) {
      myState.getGroups().remove(group);
    }
  }

  @Override
  public long getModificationCount() {
    synchronized (myStateLock) {
      return myModCounter.get() + myState.getModificationCount();
    }
  }

  private void updateProjectInfo(@NotNull Project project, @NotNull WindowManagerImpl windowManager) {
    IdeFrameImpl frame = windowManager.getFrame(project);
    if (frame == null) {
      return;
    }

    String workspaceId = ProjectKt.getStateStore(project).getProjectWorkspaceId();

    // ensure that last closed project frame bounds will be used as newly created project frame bounds (if will be no another focused opened project)
    FrameInfo frameInfo = ProjectFrameBounds.getInstance(project).getActualFrameInfoInDeviceSpace(frame, windowManager);
    String path = getProjectPath(project);
    synchronized (myStateLock) {
      RecentProjectMetaInfo info = myState.getAdditionalInfo().get(path);
      if (info != null) {
        if (info.getFrame() != frameInfo) {
          info.setFrame(frameInfo);
        }
        info.setProjectWorkspaceId(workspaceId);
      }
    }

    if (workspaceId != null && Registry.is("ide.project.loading.show.last.state")) {
      frame.takeASelfie(workspaceId);
    }
  }

  static final class MyAppLifecycleListener implements AppLifecycleListener {
    @Override
    public void projectOpenFailed() {
      getInstanceEx().updateLastProjectPath();
    }

    @Override
    public void appClosing() {
      if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
        return;
      }

      WindowManagerImpl windowManager = (WindowManagerImpl)WindowManager.getInstance();
      RecentProjectsManagerBase manager = getInstanceEx();
      for (Project project : ProjectManager.getInstance().getOpenProjects()) {
        manager.updateProjectInfo(project, windowManager);
      }
    }

    @Override
    public void projectFrameClosed() {
      // ProjectManagerListener.projectClosed cannot be used to call updateLastProjectPath,
      // because called even if project closed on app exit
      getInstanceEx().updateLastProjectPath();
    }
  }
}
