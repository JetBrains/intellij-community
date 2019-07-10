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
import com.intellij.openapi.wm.impl.FrameInfo;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.openapi.wm.impl.ProjectFrameBounds;
import com.intellij.openapi.wm.impl.SystemDock;
import com.intellij.openapi.wm.impl.welcomeScreen.RecentProjectPanel;
import com.intellij.platform.PlatformProjectOpenProcessor;
import com.intellij.project.ProjectKt;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
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
public class RecentProjectsManagerBase extends RecentProjectsManager implements PersistentStateComponent<RecentProjectsManagerBase.State>,
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

  public static final class State {
    @SuppressWarnings({"MissingDeprecatedAnnotation", "DeprecatedIsStillUsed"})
    @Deprecated
    public final List<String> recentPaths = new SmartList<>();

    @Deprecated
    public final List<String> openPaths = new SmartList<>();

    public final List<ProjectGroup> groups = new SmartList<>();
    public String pid;
    public final LinkedHashMap<String, RecentProjectMetaInfo> additionalInfo = new LinkedHashMap<>();

    public String lastProjectLocation;

    private void validateRecentProjects(@NotNull AtomicLong modCounter) {
      int limit = Registry.intValue("ide.max.recent.projects");
      if (additionalInfo.size() <= limit) {
        return;
      }

      while (additionalInfo.size() > limit) {
        Iterator<String> iterator = additionalInfo.keySet().iterator();
        while (iterator.hasNext()) {
          String path = iterator.next();
          if (!additionalInfo.get(path).opened) {
            iterator.remove();
            break;
          }
        }
      }
      modCounter.incrementAndGet();
    }
  }

  private final Object myStateLock = new Object();
  private State myState = new State();

  private boolean myBatchOpening;

  @Override
  public State getState() {
    synchronized (myStateLock) {
      myState.recentPaths.clear();
      myState.recentPaths.addAll(ContainerUtil.reverse(new ArrayList<>(myState.additionalInfo.keySet())));

      if (myState.pid == null) {
        //todo[kb] uncomment when we will fix JRE-251 The pid is needed for 3rd parties like Toolbox App to show the project is open now
        myState.pid = "";//OSProcessUtil.getApplicationPid();
      }
      return myState;
    }
  }

  @NotNull
  protected final State getStateInner() {
    synchronized (myStateLock) {
      return myState;
    }
  }

  @Override
  public void loadState(@NotNull final State state) {
    synchronized (myStateLock) {
      myState = state;
      myState.pid = null;

      List<String> openPaths = myState.openPaths;
      if (!openPaths.isEmpty()) {
        for (String path : openPaths) {
          RecentProjectMetaInfo info = myState.additionalInfo.get(path);
          if (info != null) {
            info.opened = true;
          }
        }
        openPaths.clear();
      }
    }
  }

  @Override
  public void removePath(@Nullable @SystemIndependent String path) {
    if (path == null) {
      return;
    }

    synchronized (myStateLock) {
      myState.additionalInfo.remove(path);
      for (ProjectGroup group : myState.groups) {
        group.removeProject(path);
      }
    }

    // for simplicity, for now just increment and don't check is something really changed
    myModCounter.incrementAndGet();
  }

  @Override
  public boolean hasPath(@SystemIndependent String path) {
    synchronized (myStateLock) {
      return myState.additionalInfo.containsKey(path);
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
      return myState.lastProjectLocation;
    }
  }

  @Override
  public void setLastProjectCreationLocation(@Nullable @SystemIndependent String lastProjectLocation) {
    String location = StringUtil.nullize(lastProjectLocation, true);
    String newValue = PathUtil.toSystemIndependentName(location);
    synchronized (myStateLock) {
      if (!Objects.equals(newValue, myState.lastProjectLocation)) {
        myState.lastProjectLocation = newValue;
        myModCounter.incrementAndGet();
      }
    }
  }

  @Override
  public final void updateLastProjectPath() {
    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    synchronized (myStateLock) {
      for (RecentProjectMetaInfo info : myState.additionalInfo.values()) {
        info.opened = false;
      }

      for (Project project : openProjects) {
        String path = getProjectPath(project);
        if (path != null) {
          RecentProjectMetaInfo info = myState.additionalInfo.get(path);
          if (info != null) {
            info.opened = true;
            info.projectOpenTimestamp = System.currentTimeMillis();
            info.displayName = getProjectDisplayName(project);
          }
        }
      }

      myState.validateRecentProjects(myModCounter);
    }

    // for simplicity, for now just increment and don't check is something really changed
    myModCounter.incrementAndGet();
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
      paths = new LinkedHashSet<>(ContainerUtil.reverse(new ArrayList<>(myState.additionalInfo.keySet())));
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
        groups = new ArrayList<>(myState.groups);
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
      RecentProjectMetaInfo info = myState.additionalInfo.get(path);
      displayName = info == null ? null : info.displayName;
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
      ProjectGroup group = getProjectGroup(path);
      removePath(path);

      if (group != null) {
        List<String> projects = group.getProjects();
        projects.add(0, path);
        group.save(projects);
      }

      // remove instead of get to re-order
      RecentProjectMetaInfo info = myState.additionalInfo.remove(path);
      if (info == null) {
        info = new RecentProjectMetaInfo();
      }
      myState.additionalInfo.put(path, info);

      ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();
      info.displayName = getProjectDisplayName(project);
      info.projectWorkspaceId = ProjectKt.getStateStore(project).getProjectWorkspaceId();
      info.frame = ProjectFrameBounds.getInstance(project).getState();
      info.build = appInfo.getBuild().asString();
      info.productionCode = appInfo.getBuild().getProductCode();
      info.eap = appInfo.isEAP();
      info.binFolder = FileUtilRt.toSystemIndependentName(PathManager.getBinPath());
      info.projectOpenTimestamp = System.currentTimeMillis();
      info.buildTimestamp = appInfo.getBuildDate().getTimeInMillis();
      info.metadata = getRecentProjectMetadata(path, project);
    }

    myModCounter.incrementAndGet();
  }

  @SuppressWarnings("unused")
  @Nullable
  protected String getRecentProjectMetadata(@SystemIndependent String path, @NotNull Project project) {
    return null;
  }

  @Nullable
  private ProjectGroup getProjectGroup(@NotNull @SystemIndependent String path) {
    synchronized (myStateLock) {
      for (ProjectGroup group : myState.groups) {
        if (group.getProjects().contains(path)) {
          return group;
        }
      }
    }
    return null;
  }

  @Nullable
  @SystemIndependent
  protected String getProjectPath(@NotNull Project project) {
    return PathUtil.toSystemIndependentName(project.getPresentableUrl());
  }

  @Nullable
  public Project doOpenProject(@NotNull @SystemIndependent String projectPath, @NotNull OpenProjectTask openProjectOptions) {
    Path projectFile = Paths.get(projectPath);

    Project existing = ProjectUtil.findAndFocusExistingProjectForPath(projectFile);
    if (existing != null) {
      return existing;
    }

    if (Files.isDirectory(projectFile.resolve(Project.DIRECTORY_STORE_FOLDER))) {
      return PlatformProjectOpenProcessor.openExistingDirectoryBasedProjectInANewFrame(projectFile, projectFile, openProjectOptions, -1, null);
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

      synchronized (manager.myStateLock) {
        manager.myNameCache.put(path, project.getName());
      }
    }

    @Override
    public void projectClosed(@NotNull final Project project) {
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

    @Override
    public void projectClosingBeforeSave(@NotNull Project project) {
      JFrame frame = WindowManager.getInstance().getFrame(project);
      if (frame instanceof IdeFrameImpl) {
        String workspaceId = ProjectKt.getStateStore(project).getProjectWorkspaceId();
        if (workspaceId != null && Registry.is("ide.project.loading.show.last.state")) {
          ((IdeFrameImpl)frame).takeASelfie(workspaceId);
        }
      }
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
      for (RecentProjectMetaInfo info : myState.additionalInfo.values()) {
        if (info.opened) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public void reopenLastProjectOnStart() {
    doReopenLastProject();
  }

  protected void doReopenLastProject() {
    if (!GeneralSettings.getInstance().isReopenLastProject()) {
      return;
    }

    List<Map.Entry<String, RecentProjectMetaInfo>> openPaths;
    synchronized (myStateLock) {
      openPaths = ContainerUtil.reverse(ContainerUtil.findAll(myState.additionalInfo.entrySet(), it -> it.getValue().opened));
    }

    try {
      myBatchOpening = true;
      for (Map.Entry<String, RecentProjectMetaInfo> it : openPaths) {
        // https://youtrack.jetbrains.com/issue/IDEA-166321
        OpenProjectTask options = new OpenProjectTask(/* forceOpenInNewFrame = */ true);
        options.setFrame(it.getValue().frame);
        options.setProjectWorkspaceId(it.getValue().projectWorkspaceId);
        doOpenProject(it.getKey(), options);
      }
    }
    finally {
      myBatchOpening = false;
    }
  }

  public boolean isBatchOpening() {
    return myBatchOpening;
  }

  @Override
  @NotNull
  public List<ProjectGroup> getGroups() {
    synchronized (myStateLock) {
      return Collections.unmodifiableList(myState.groups);
    }
  }

  @Override
  public void addGroup(@NotNull ProjectGroup group) {
    synchronized (myStateLock) {
      if (!myState.groups.contains(group)) {
        myState.groups.add(group);
        myModCounter.incrementAndGet();
      }
    }
  }

  @Override
  public void removeGroup(@NotNull ProjectGroup group) {
    synchronized (myStateLock) {
      myState.groups.remove(group);
      myModCounter.incrementAndGet();
    }
  }

  @Override
  public long getModificationCount() {
    return myModCounter.get();
  }

  static final class MyAppLifecycleListener implements AppLifecycleListener {
    @Override
    public void projectOpenFailed() {
      getInstanceEx().updateLastProjectPath();
    }

    @Override
    public void projectFrameClosed() {
      // ProjectManagerListener.projectClosed cannot be used to call updateLastProjectPath,
      // because called even if project closed on app exit
      getInstanceEx().updateLastProjectPath();
    }
  }

  public final static class RecentProjectMetaInfo {
    @Attribute
    public String displayName;

    @Attribute
    public boolean opened;

    public String build;
    public String productionCode;
    public boolean eap;
    public String binFolder;
    public long projectOpenTimestamp;
    public long buildTimestamp;
    public String metadata;

    @Attribute
    public String projectWorkspaceId;

    @Property(surroundWithTag = false)
    public FrameInfo frame;
  }
}
