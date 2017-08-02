/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.wm.impl.SystemDock;
import com.intellij.project.ProjectKt;
import com.intellij.ui.IconDeferrer;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.imgscalr.Scalr;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.net.MalformedURLException;
import java.util.*;
import java.util.List;

/**
 * @author yole
 * @author Konstantin Bulenkov
 */
public abstract class RecentProjectsManagerBase extends RecentProjectsManager implements PersistentStateComponent<RecentProjectsManagerBase.State> {
  private static final int MAX_PROJECTS_IN_MAIN_MENU = 6;
  private static final Map<String, MyIcon> ourProjectIcons = new HashMap<>();
  private static Icon ourSmallAppIcon;
  private final Alarm myNamesResolver = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, ApplicationManager.getApplication());
  private final Set<String> myNamesToResolve = new HashSet<>(MAX_PROJECTS_IN_MAIN_MENU);

  public static RecentProjectsManagerBase getInstanceEx() {
    return (RecentProjectsManagerBase)RecentProjectsManager.getInstance();
  }

  public static class State {
    public List<String> recentPaths = new SmartList<>();
    public List<String> openPaths = new SmartList<>();
    public Map<String, String> names = ContainerUtil.newLinkedHashMap();
    public List<ProjectGroup> groups = new SmartList<>();
    public String lastPath;
    public String pid;

    private static String getPid() {
        String processName = ManagementFactory.getRuntimeMXBean().getName();
        return processName.split("@")[0];
    }

    public Map<String, RecentProjectMetaInfo> additionalInfo = ContainerUtil.newLinkedHashMap();

    public String lastProjectLocation;

    void validateRecentProjects() {
      //noinspection StatementWithEmptyBody
      while (recentPaths.remove(null)) ;
      Collection<String> displayNames = names.values();
      //noinspection StatementWithEmptyBody
      while (displayNames.remove("")) ;

      while (recentPaths.size() > Registry.intValue("ide.max.recent.projects")) {
        int index = recentPaths.size() - 1;
        names.remove(recentPaths.get(index));
        recentPaths.remove(index);
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
          it.set(convert.fun(it.next()));
        }
      };

      convertList.consume(recentPaths);
      convertList.consume(openPaths);

      Map<String, String> namesCopy = new HashMap<>(names);
      names.clear();
      for (Map.Entry<String, String> entry : namesCopy.entrySet()) {
        names.put(convert.fun(entry.getKey()), entry.getValue());
      }

      for (ProjectGroup group : groups) {
        List<String> paths = new ArrayList<>(group.getProjects());
        convertList.consume(paths);
        group.save(paths);
      }

      if (lastPath != null) {
        lastPath = convert.fun(lastPath);
      }

      Map<String, RecentProjectMetaInfo> additionalInfoCopy = new HashMap<>(additionalInfo);
      additionalInfo.clear();
      for (Map.Entry<String, RecentProjectMetaInfo> entry : additionalInfoCopy.entrySet()) {
        entry.getValue().binFolder = convert.fun(entry.getValue().binFolder);
        additionalInfo.put(convert.fun(entry.getKey()), entry.getValue());
      }

      if (lastProjectLocation != null) {
        lastProjectLocation = convert.fun(lastProjectLocation);
      }
    }

    public void updateOpenProjectsTimestamps(RecentProjectsManagerBase mgr) {
      for (Project project : ProjectManager.getInstance().getOpenProjects()) {
        String path = PathUtil.toSystemIndependentName(mgr.getProjectPath(project));
        RecentProjectMetaInfo info = additionalInfo.get(path);
        if (info != null) {
          info.projectOpenTimestamp = System.currentTimeMillis();
        }
      }
    }
  }

  private final Object myStateLock = new Object();
  private State myState = new State();

  private final Map<String, String> myNameCache = Collections.synchronizedMap(new THashMap<String, String>());
  private Set<String> myDuplicatesCache = null;
  private boolean isDuplicatesCacheUpdating = false;
  private boolean myBatchOpening;

  protected RecentProjectsManagerBase(@NotNull MessageBus messageBus) {
    MessageBusConnection connection = messageBus.connect();
    connection.subscribe(AppLifecycleListener.TOPIC, new MyAppLifecycleListener());
    connection.subscribe(ProjectManager.TOPIC, new MyProjectListener());
  }

  @Override
  public State getState() {
    synchronized (myStateLock) {
      if (myState.pid == null) {
        myState.pid = State.getPid();
      }
      updateLastProjectPath();
      myState.validateRecentProjects();
      myState.updateOpenProjectsTimestamps(this);
      return myState;
    }
  }

  @Override
  public void loadState(final State state) {
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

  protected void removeDuplicates(State state) {
    for (String path : new ArrayList<>(state.recentPaths)) {
      if (path.endsWith("/")) {
        state.recentPaths.remove(path);
        state.additionalInfo.remove(path);
        state.openPaths.remove(path);
      }
    }
  }

  private static void removePathFrom(List<String> items, String path) {
    for (Iterator<String> iterator = items.iterator(); iterator.hasNext();) {
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
    return myState.lastProjectLocation;
  }

  @Override
  public void setLastProjectCreationLocation(@Nullable @SystemIndependent String lastProjectLocation) {
    String location = StringUtil.nullize(lastProjectLocation, true);
    myState.lastProjectLocation = PathUtil.toSystemIndependentName(location);
  }

  @Override
  @SystemIndependent
  public String getLastProjectPath() {
    return myState.lastPath;
  }

  @Override
  public void updateLastProjectPath() {
    final Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    synchronized (myStateLock) {
      myState.openPaths.clear();
      if (openProjects.length == 0) {
        myState.lastPath = null;
      }
      else {
        myState.lastPath = getProjectPath(openProjects[openProjects.length - 1]);
        for (Project openProject : openProjects) {
          String path = getProjectPath(openProject);
          if (path != null) {
            myState.openPaths.add(path);
            myState.names.put(path, getProjectDisplayName(openProject));
          }
        }
      }
    }
  }

  @NotNull
  protected String getProjectDisplayName(@NotNull Project project) {
    return "";
  }

  @Nullable
  public static Icon getProjectIcon(@SystemIndependent String path, boolean isDark) {
    final MyIcon icon = ourProjectIcons.get(path);
    if (icon != null) {
      return icon.getIcon();
    }
    return IconDeferrer.getInstance().defer(EmptyIcon.ICON_16,
                                            Pair.create(path, isDark),
                                            p -> calculateIcon(p.first, p.second));
  }

  @Nullable
  protected static Icon calculateIcon(@SystemIndependent String path, boolean isDark) {
    File file = new File(path + (isDark ? "/.idea/icon_dark.png" : "/.idea/icon.png"));
    if (file.exists()) {
      final long timestamp = file.lastModified();
      MyIcon icon = ourProjectIcons.get(path);
      if (icon != null && icon.getTimestamp() == timestamp) {
        return icon.getIcon();
      }
      try {
        Icon ico = createIcon(file);
        icon = new MyIcon(ico, timestamp);
        ourProjectIcons.put(path, icon);
        return icon.getIcon();
      }
      catch (Exception e) {
        e.printStackTrace();
      }
    }
    return null;
  }

  @NotNull
  public static Icon createIcon(File file) {
    final BufferedImage image = loadAndScaleImage(file);
    return toRetinaAwareIcon(image);
  }

  @NotNull
  protected static Icon toRetinaAwareIcon(final BufferedImage image) {
    return new Icon() {
      @Override
      public void paintIcon(Component c, Graphics g, int x, int y) {
        // [tav] todo: the icon is created in def screen scale
        if (UIUtil.isJreHiDPI()) {
          final Graphics2D newG = (Graphics2D)g.create(x, y, image.getWidth(), image.getHeight());
          float s = JBUI.sysScale();
          newG.scale(1/s, 1/s);
          newG.drawImage(image, (int)(x / s), (int)(y / s), null);
          newG.scale(1, 1);
          newG.dispose();
        }
        else {
          g.drawImage(image, x, y, null);
        }
      }

      @Override
      public int getIconWidth() {
        return UIUtil.isJreHiDPI() ? (int)(image.getWidth() / JBUI.sysScale()) : image.getWidth();
      }

      @Override
      public int getIconHeight() {
        return UIUtil.isJreHiDPI() ? (int)(image.getHeight() / JBUI.sysScale()) : image.getHeight();
      }
    };
  }

  private static BufferedImage loadAndScaleImage(File file) {
    try {
      Image img = ImageLoader.loadFromUrl(file.toURL());
      return Scalr.resize(ImageUtil.toBufferedImage(img), Scalr.Method.ULTRA_QUALITY, UIUtil.isRetina() ? 32 : (int)JBUI.pixScale(16));
    }
    catch (MalformedURLException e) {//
    }
    return null;
  }

  public static Icon getProjectOrAppIcon(String path) {
    Icon icon = getProjectIcon(path, UIUtil.isUnderDarcula());
    if (icon != null) {
      return icon;
    }

    if (UIUtil.isUnderDarcula()) {
      //No dark icon for this project
      icon = getProjectIcon(path, false);
      if (icon != null) {
        return icon;
      }
    }

    return getSmallApplicationIcon();
  }

  protected static Icon getSmallApplicationIcon() {
    if (ourSmallAppIcon == null) {
      try {
        Icon appIcon = IconLoader.findIcon(ApplicationInfoEx.getInstanceEx().getIconUrl());

        if (appIcon != null) {
          if (appIcon.getIconWidth() == JBUI.pixScale(16) && appIcon.getIconHeight() == JBUI.pixScale(16)) {
            ourSmallAppIcon = appIcon;
          } else {
            BufferedImage image = ImageUtil.toBufferedImage(IconUtil.toImage(appIcon));
            image = Scalr.resize(image, Scalr.Method.ULTRA_QUALITY, UIUtil.isRetina() ? 32 : (int)JBUI.pixScale(16));
            ourSmallAppIcon = toRetinaAwareIcon(image);
          }
        }
      }
      catch (Exception e) {//
      }
      if (ourSmallAppIcon == null) {
        ourSmallAppIcon = EmptyIcon.ICON_16;
      }
    }

    return ourSmallAppIcon;
  }

  private Set<String> getDuplicateProjectNames(Set<String> openedPaths, Set<String> recentPaths) {
    if (myDuplicatesCache != null) {
      return myDuplicatesCache;
    }

    if (!isDuplicatesCacheUpdating) {
      isDuplicatesCacheUpdating = true; //assuming that this check happens only on EDT. So, no synchronised block or double-checked locking needed
      Set<String> names = ContainerUtil.newHashSet();
      final HashSet<String> duplicates = ContainerUtil.newHashSet();
      ArrayList<String> list = ContainerUtil.newArrayList(ContainerUtil.concat(openedPaths, recentPaths));
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        for (String path : list) {
          if (!names.add(getProjectName(path))) {
            duplicates.add(path);
          }
        }
        myDuplicatesCache = duplicates;
        isDuplicatesCacheUpdating = false;
      });
    }
    return ContainerUtil.newHashSet();
  }

  @Override
  public AnAction[] getRecentProjectsActions(boolean forMainMenu) {
    return getRecentProjectsActions(forMainMenu, false);
  }

  @Override
  public AnAction[] getRecentProjectsActions(boolean forMainMenu, boolean useGroups) {
    final Set<String> paths;
    synchronized (myStateLock) {
      myState.validateRecentProjects();
      paths = ContainerUtil.newLinkedHashSet(myState.recentPaths);
    }

    Set<String> openedPaths = new THashSet<>();
    for (Project openProject : ProjectManager.getInstance().getOpenProjects()) {
      ContainerUtil.addIfNotNull(openedPaths, getProjectPath(openProject));
    }

    paths.remove(null);
    paths.removeAll(openedPaths);

    List<AnAction> actions = new SmartList<>();
    Set<String> duplicates = getDuplicateProjectNames(openedPaths, paths);
    if (useGroups) {
      final List<ProjectGroup> groups = new ArrayList<>(new ArrayList<>(myState.groups));
      final List<String> projectPaths = new ArrayList<>(paths);
      Collections.sort(groups, new Comparator<ProjectGroup>() {
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
          final AnAction action = createOpenAction(path, duplicates);
          if (action != null) {
            children.add(action);

            if (forMainMenu && children.size() >= MAX_PROJECTS_IN_MAIN_MENU) {
              break;
            }
          }
        }
        actions.add(new ProjectGroupActionGroup(group, children));
        if (group.isExpanded()) {
          for (AnAction child : children) {
            actions.add(child);
          }
        }
      }
    }

    for (final String path : paths) {
      final AnAction action = createOpenAction(path, duplicates);
      if (action != null) {
        actions.add(action);
      }
    }

    if (actions.isEmpty()) {
      return AnAction.EMPTY_ARRAY;
    }

    return actions.toArray(new AnAction[actions.size()]);
  }

  private AnAction createOpenAction(@SystemIndependent String path, Set<String> duplicates) {
    String projectName = getProjectName(path);
    String displayName;
    synchronized (myStateLock) {
      displayName = myState.names.get(path);
    }
    if (StringUtil.isEmptyOrSpaces(displayName)) {
      displayName = duplicates.contains(path) ? path : projectName;
    }

    // It's better don't to remove non-existent projects. Sometimes projects stored
    // on USB-sticks or flash-cards, and it will be nice to have them in the list
    // when USB device or SD-card is mounted
    //if (new File(path).exists()) {
      return new ReopenProjectAction(path, projectName, displayName);
    //}
    //return null;
  }

  private void markPathRecent(@SystemIndependent String path) {
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
      myState.additionalInfo.put(path, RecentProjectMetaInfo.create());
    }
  }

  @Nullable
  private ProjectGroup getProjectGroup(@SystemIndependent String path) {
    if (path == null) return null;
    for (ProjectGroup group : myState.groups) {
      if (group.getProjects().contains(path)) {
        return group;
      }
    }
    return null;
  }

  @Nullable
  @SystemIndependent
  protected abstract String getProjectPath(@NotNull Project project);

  protected abstract void doOpenProject(@NotNull String projectPath, @Nullable Project projectToClose, boolean forceOpenInNewFrame);

  private class MyProjectListener implements ProjectManagerListener {
    @Override
    public void projectOpened(final Project project) {
      String path = getProjectPath(project);
      if (path != null) {
        markPathRecent(path);
      }
      updateUI();
    }

    private void updateUI() {
      if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
        SystemDock.updateMenu();
      }
    }

    @Override
    public void projectClosing(Project project) {
      String path = getProjectPath(project);
      if (path == null) return;
      
      synchronized (myStateLock) {
        myState.names.put(path, getProjectDisplayName(project));
      }
      myNameCache.put(path, project.getName());
    }

    @Override
    public void projectClosed(final Project project) {
      Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
      if (openProjects.length > 0) {
        String path = getProjectPath(openProjects[openProjects.length - 1]);
        if (path != null) {
          markPathRecent(path);
        }
      }
      updateUI();
    }
  }

  @NotNull
  public String getProjectName(@NotNull String path) {
    String cached = myNameCache.get(path);
    if (cached != null) {
      return cached;
    }
    myNamesResolver.cancelAllRequests();
    synchronized (myNamesToResolve) {
      myNamesToResolve.add(path);
    }
    myNamesResolver.addRequest(() -> {
      final Set<String> paths;
      synchronized (myNamesToResolve) {
        paths = new HashSet<>(myNamesToResolve);
        myNamesToResolve.clear();
      }
      for (String p : paths) {
        myNameCache.put(p, readProjectName(p));
      }
    }, 50);
    String name = new File(path).getName();
    return path.endsWith(".ipr") ? FileUtilRt.getNameWithoutExtension(name) : name;
  }

  @Override
  public void clearNameCache() {
  }

  private static String readProjectName(@NotNull String path) {
    final File file = new File(path);
    if (file.isDirectory()) {
      final File nameFile = new File(new File(path, Project.DIRECTORY_STORE_FOLDER), ProjectImpl.NAME_FILE);
      if (nameFile.exists()) {
        try {
          final BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(nameFile), CharsetToolkit.UTF8_CHARSET));
          try {
            String name = in.readLine();
            if (!StringUtil.isEmpty(name)) {
              return name.trim();
            }
          }
          finally {
            in.close();
          }
        }
        catch (IOException ignored) { }
      }
      return file.getName();
    }
    else {
      return FileUtilRt.getNameWithoutExtension(file.getName());
    }
  }

  protected boolean willReopenProjectOnStart() {
    return GeneralSettings.getInstance().isReopenLastProject() && getLastProjectPath() != null;
  }

  protected void doReopenLastProject() {
    GeneralSettings generalSettings = GeneralSettings.getInstance();
    if (generalSettings.isReopenLastProject()) {
      Set<String> openPaths;
      boolean forceNewFrame = true;
      synchronized (myStateLock) {
        openPaths = ContainerUtil.newLinkedHashSet(myState.openPaths);
        if (openPaths.isEmpty()) {
          openPaths = ContainerUtil.createMaybeSingletonSet(myState.lastPath);
          forceNewFrame = false;
        }
      }
      try {
        myBatchOpening = true;
        for (String openPath : openPaths) {
          // https://youtrack.jetbrains.com/issue/IDEA-166321
          if (ProjectKt.isValidProjectPath(openPath, true)) {
            doOpenProject(openPath, null, forceNewFrame);
          }
        }
      }
      finally {
        myBatchOpening = false;
      }
    }
  }

  public boolean isBatchOpening() {
    return myBatchOpening;
  }

  @Override
  public List<ProjectGroup> getGroups() {
    return Collections.unmodifiableList(myState.groups);
  }

  @Override
  public void addGroup(ProjectGroup group) {
    if (!myState.groups.contains(group)) {
      myState.groups.add(group);
    }
  }

  @Override
  public void removeGroup(ProjectGroup group) {
    myState.groups.remove(group);
  }

  private class MyAppLifecycleListener implements AppLifecycleListener {
    @Override
    public void appFrameCreated(final String[] commandLineArgs, @NotNull final Ref<Boolean> willOpenProject) {
      if (willReopenProjectOnStart()) {
        willOpenProject.set(Boolean.TRUE);
      }
    }

    @Override
    public void appStarting(Project projectFromCommandLine) {
      if (projectFromCommandLine != null) return;
      doReopenLastProject();
    }

    @Override
    public void projectFrameClosed() {
      updateLastProjectPath();
    }

    @Override
    public void projectOpenFailed() {
      updateLastProjectPath();
    }

    @Override
    public void appClosing() {
      updateLastProjectPath();
    }
  }

  private static class MyIcon extends Pair<Icon, Long> {
    public MyIcon(Icon icon, Long timestamp) {
      super(icon, timestamp);
    }

    public Icon getIcon() {
      return first;
    }

    public long getTimestamp() {
      return second;
    }
  }

  public static class RecentProjectMetaInfo {
    public String build;
    public String productionCode;
    public boolean eap;
    public String binFolder;
    public long projectOpenTimestamp;
    public long buildTimestamp;

    public static RecentProjectMetaInfo create() {
      RecentProjectMetaInfo info = new RecentProjectMetaInfo();
      info.build = ApplicationInfoEx.getInstanceEx().getBuild().asString();
      info.productionCode = ApplicationInfoEx.getInstanceEx().getBuild().getProductCode();
      info.eap = ApplicationInfoEx.getInstanceEx().isEAP();
      info.binFolder = PathUtil.toSystemIndependentName(PathManager.getBinPath());
      info.projectOpenTimestamp = System.currentTimeMillis();
      info.buildTimestamp = ApplicationInfoEx.getInstanceEx().getBuildDate().getTimeInMillis();
      return info;
    }
  }
}
