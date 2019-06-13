// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.configurationStore.StorageUtilKt;
import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.idea.SplashManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.SystemDock;
import com.intellij.openapi.wm.impl.WindowManagerImpl;
import com.intellij.openapi.wm.impl.welcomeScreen.RecentProjectPanel;
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
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;

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
    public final List<String> recentPaths = new SmartList<>();
    public final List<String> openPaths = new SmartList<>();
    public final Map<String, String> names = new LinkedHashMap<>();
    public final List<ProjectGroup> groups = new SmartList<>();
    public String lastPath;
    public String pid;
    public final Map<String, RecentProjectMetaInfo> additionalInfo = new LinkedHashMap<>();

    public String lastProjectLocation;

    private void validateRecentProjects(@NotNull AtomicLong modCounter) {
      while (recentPaths.remove(null)) {
        modCounter.incrementAndGet();
      }

      Collection<String> displayNames = names.values();
      while (displayNames.remove("")) {
        modCounter.incrementAndGet();
      }

      while (recentPaths.size() > Registry.intValue("ide.max.recent.projects")) {
        int index = recentPaths.size() - 1;
        names.remove(recentPaths.get(index));
        recentPaths.remove(index);
        modCounter.incrementAndGet();
      }
    }

    // TODO Should be removed later (required to convert the already saved system-dependent paths).
    private void makePathsSystemIndependent() {
      ApplicationInfo appInfo = ApplicationInfo.getInstance();
      String version = appInfo.getMajorVersion() + "." + appInfo.getMinorVersion();
      PathMacroManager pathMacroManager = PathMacroManager.getInstance(ApplicationManager.getApplication());
      Function<String, String> convert = depPath -> {
        String result = PathUtil.toSystemIndependentName(depPath);
        if (!result.startsWith("$APP") && result.contains("2017.1")) {
          String migrated = result.replace("2017.1", version);
          // check for possible PathMacroUtil.APPLICATION_*
          if (pathMacroManager.collapsePath(migrated).startsWith("$APP")) {
            return migrated;
          }
        }
        return result;
      };
      Consumer<List<String>> convertList = o -> {
        for (ListIterator<String> it = o.listIterator(); it.hasNext(); ) {
          it.set(convert.apply(it.next()));
        }
      };

      convertList.accept(recentPaths);
      convertList.accept(openPaths);

      Map<String, String> namesCopy = new LinkedHashMap<>(names);
      names.clear();
      for (Map.Entry<String, String> entry : namesCopy.entrySet()) {
        names.put(convert.apply(entry.getKey()), entry.getValue());
      }

      for (ProjectGroup group : groups) {
        List<String> paths = new ArrayList<>(group.getProjects());
        convertList.accept(paths);
        group.save(paths);
      }

      if (lastPath != null) {
        lastPath = convert.apply(lastPath);
      }

      Map<String, RecentProjectMetaInfo> additionalInfoCopy = new LinkedHashMap<>(additionalInfo);
      additionalInfo.clear();
      for (Map.Entry<String, RecentProjectMetaInfo> entry : additionalInfoCopy.entrySet()) {
        entry.getValue().binFolder = convert.apply(entry.getValue().binFolder);
        additionalInfo.put(convert.apply(entry.getKey()), entry.getValue());
      }

      if (lastProjectLocation != null) {
        lastProjectLocation = convert.apply(lastProjectLocation);
      }
    }
  }

  private final Object myStateLock = new Object();
  private State myState = new State();

  private boolean myBatchOpening;

  @Override
  public State getState() {
    synchronized (myStateLock) {
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
    state.makePathsSystemIndependent();
    removeDuplicates(state);
    if (state.lastPath != null) {
      File lastFile = new File(PathUtil.toSystemDependentName(state.lastPath));
      if (!lastFile.exists() ||
          lastFile.isDirectory() && !new File(lastFile, Project.DIRECTORY_STORE_FOLDER).exists()) {
        state.lastPath = null;
      }
    }
    synchronized (myStateLock) {
      myState = state;
      myState.pid = null;
    }
  }

  private static void removeDuplicates(@NotNull State state) {
    for (String path : new ArrayList<>(state.recentPaths)) {
      if (path.endsWith("/")) {
        state.recentPaths.remove(path);
        state.additionalInfo.remove(path);
        state.openPaths.remove(path);
      }
    }
  }

  private static void removePathFrom(@NotNull List<String> items, @NotNull String path) {
    for (Iterator<String> iterator = items.iterator(); iterator.hasNext(); ) {
      final String next = iterator.next();
      if (SystemInfo.isFileSystemCaseSensitive ? path.equals(next) : path.equalsIgnoreCase(next)) {
        iterator.remove();
      }
    }
  }

  @Override
  public void removePath(@Nullable @SystemIndependent String path) {
    if (path == null) {
      return;
    }

    synchronized (myStateLock) {
      removePathFrom(myState.recentPaths, path);
      myState.names.remove(path);
      for (ProjectGroup group : myState.groups) {
        group.removeProject(path);
      }
    }

    // for simplicity, for now just increment and don't check is something really changed
    myModCounter.incrementAndGet();
  }

  @Override
  public boolean hasPath(@SystemIndependent String path) {
    final State state = getState();
    return state != null && state.recentPaths.contains(path);
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
  @SystemIndependent
  public String getLastProjectPath() {
    synchronized (myStateLock) {
      return myState.lastPath;
    }
  }

  @Override
  public final void updateLastProjectPath() {
    final Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    synchronized (myStateLock) {
      List<String> openPaths = myState.openPaths;
      openPaths.clear();
      if (openProjects.length == 0) {
        myState.lastPath = null;
      }
      else {
        myState.lastPath = getProjectPath(openProjects[openProjects.length - 1]);
        for (Project openProject : openProjects) {
          String path = getProjectPath(openProject);
          if (path != null) {
            openPaths.add(path);
            myState.names.put(path, getProjectDisplayName(openProject));
          }
        }
      }

      myState.validateRecentProjects(myModCounter);
      updateOpenProjectsTimestamps(openProjects);
    }

    // for simplicity, for now just increment and don't check is something really changed
    myModCounter.incrementAndGet();
  }

  private void updateOpenProjectsTimestamps(@NotNull Project[] openProjects) {
    Map<String, RecentProjectMetaInfo> additionalInfo = myState.additionalInfo;
    for (Project project : openProjects) {
      String path = getProjectPath(project);
      RecentProjectMetaInfo info = path == null ? null : additionalInfo.get(path);
      if (info != null) {
        info.projectOpenTimestamp = System.currentTimeMillis();
      }
    }
  }

  @NotNull
  protected String getProjectDisplayName(@NotNull Project project) {
    return "";
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
    final Set<String> paths;
    synchronized (myStateLock) {
      myState.validateRecentProjects(myModCounter);
      paths = new LinkedHashSet<>(myState.recentPaths);
    }

    Set<String> openedPaths = new THashSet<>();
    for (Project openProject : ProjectManager.getInstance().getOpenProjects()) {
      ContainerUtil.addIfNotNull(openedPaths, getProjectPath(openProject));
    }

    paths.remove(null);
    //paths.removeAll(openedPaths);

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

    for (final String path : paths) {
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
      displayName = myState.names.get(path);
    }
    if (StringUtil.isEmptyOrSpaces(displayName)) {
      displayName = duplicates.contains(projectName) ? FileUtil.toSystemDependentName(path) : projectName;
    }

    // It's better don't to remove non-existent projects. Sometimes projects stored
    // on USB-sticks or flash-cards, and it will be nice to have them in the list
    // when USB device or SD-card is mounted
    //if (new File(path).exists()) {
    return new ReopenProjectAction(path, projectName, displayName);
    //}
    //return null;
  }

  private void markPathRecent(@NotNull @SystemIndependent String path, @NotNull Project project) {
    synchronized (myStateLock) {
      if (path.endsWith(File.separator)) {
        path = path.substring(0, path.length() - File.separator.length());
      }
      myState.lastPath = path;
      ProjectGroup group = getProjectGroup(path);
      removePath(path);

      myState.recentPaths.add(0, path);
      if (group != null) {
        List<String> projects = group.getProjects();
        projects.add(0, path);
        group.save(projects);
      }
      myState.additionalInfo.remove(path);

      String additionalMetadata = getRecentProjectMetadata(path, project);
      myState.additionalInfo.put(path, RecentProjectMetaInfo.create(additionalMetadata));
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
  public Project doOpenProject(@NotNull @SystemIndependent String projectPath,
                               @Nullable Project projectToClose,
                               boolean forceOpenInNewFrame,
                               @Nullable IdeFrame frame) {
    VirtualFile dotIdea = LocalFileSystem.getInstance()
      .refreshAndFindFileByPath(FileUtilRt.toSystemIndependentName(projectPath) + "/" + Project.DIRECTORY_STORE_FOLDER);
    if (dotIdea != null) {
      EnumSet<PlatformProjectOpenProcessor.Option> options = EnumSet.of(PlatformProjectOpenProcessor.Option.REOPEN);
      if (forceOpenInNewFrame) options.add(PlatformProjectOpenProcessor.Option.FORCE_NEW_FRAME);
      return PlatformProjectOpenProcessor.doOpenProject(dotIdea.getParent(), projectToClose, -1, null, options, frame);
    }
    else {
      // If .idea is missing in the recent project's dir; this might mean, for instance, that 'git clean' was called.
      // Reopening such a project should be similar to opening the dir first time (and trying to import known project formats)
      // IDEA-144453 IDEA rejects opening recent project if there are no .idea subfolder
      // CPP-12106 Auto-load CMakeLists.txt on opening from Recent projects when .idea and cmake-build-debug were deleted
      return ProjectUtil.openOrImport(projectPath, projectToClose, forceOpenInNewFrame);
    }
  }

  static final class MyProjectListener implements ProjectManagerListener {
    private final RecentProjectsManagerBase manager = getInstanceEx();

    @Override
    public void projectOpened(@NotNull final Project project) {
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
        manager.myState.names.put(path, manager.getProjectDisplayName(project));
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
    @SystemIndependent String lastProjectPath = getLastProjectPath();
    return lastProjectPath != null && GeneralSettings.getInstance().isReopenLastProject() && ProjectKt.isValidProjectPath(lastProjectPath, true);
  }

  @Override
  public void reopenLastProjectOnStart() {
    doReopenLastProject(null);
  }

  protected void doReopenLastProject(@Nullable IdeFrame frame) {
    if (!GeneralSettings.getInstance().isReopenLastProject()) {
      return;
    }

    Set<String> openPaths;
    boolean forceNewFrame = true;
    synchronized (myStateLock) {
      openPaths = new LinkedHashSet<>(myState.openPaths);
      if (openPaths.isEmpty()) {
        openPaths = ContainerUtil.createMaybeSingletonSet(myState.lastPath);
        forceNewFrame = false;
      }
    }

    if (!openPaths.isEmpty() && frame == null) {
      Activity activity = StartUpMeasurer.start("showFrame");
      frame = ((WindowManagerImpl)WindowManager.getInstance()).showFrame(SplashManager.getHideTask());
      activity.end();
    }

    try {
      myBatchOpening = true;
      boolean usedFrame = false;
      for (String openPath : openPaths) {
        // https://youtrack.jetbrains.com/issue/IDEA-166321
        if (ProjectKt.isValidProjectPath(openPath, true)) {
          doOpenProject(openPath, null, forceNewFrame, usedFrame ? null : frame);
          usedFrame = true;
        }
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
    public String build;
    public String productionCode;
    public boolean eap;
    public String binFolder;
    public long projectOpenTimestamp;
    public long buildTimestamp;
    public String metadata;

    public static RecentProjectMetaInfo create(String metadata) {
      RecentProjectMetaInfo info = new RecentProjectMetaInfo();
      info.build = ApplicationInfoEx.getInstanceEx().getBuild().asString();
      info.productionCode = ApplicationInfoEx.getInstanceEx().getBuild().getProductCode();
      info.eap = ApplicationInfoEx.getInstanceEx().isEAP();
      info.binFolder = PathUtil.toSystemIndependentName(PathManager.getBinPath());
      info.projectOpenTimestamp = System.currentTimeMillis();
      info.buildTimestamp = ApplicationInfoEx.getInstanceEx().getBuildDate().getTimeInMillis();
      info.metadata = metadata;
      return info;
    }
  }
}
