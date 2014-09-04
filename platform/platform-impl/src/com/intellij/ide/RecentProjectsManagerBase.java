/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.impl.SystemDock;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.*;

/**
 * @author yole
 */
public abstract class RecentProjectsManagerBase implements ProjectManagerListener, PersistentStateComponent<RecentProjectsManagerBase.State> {
  public static RecentProjectsManagerBase getInstance() {
    return ServiceManager.getService(RecentProjectsManagerBase.class);
  }

  public static class State {
    public List<String> recentPaths = ContainerUtil.newArrayList();
    public List<String> openPaths = ContainerUtil.newArrayList();
    public Map<String, String> names = ContainerUtil.newLinkedHashMap();
    public String lastPath;

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

    void removePath(String path) {
      recentPaths.remove(path);
      names.remove(path);
    }
  }

  private final Object myStateLock = new Object();
  private State myState = new State();

  private final Map<String, String> myNameCache = Collections.synchronizedMap(new HashMap<String, String>());

  protected RecentProjectsManagerBase(MessageBus messageBus) {
    messageBus.connect().subscribe(AppLifecycleListener.TOPIC, new MyAppLifecycleListener());
  }

  @Override
  public State getState() {
    synchronized (myStateLock) {
      myState.validateRecentProjects();
      return myState;
    }
  }

  @Override
  public void loadState(final State state) {
    synchronized (myStateLock) {
      myState = state;
      if (myState.lastPath != null && !new File(myState.lastPath).exists()) {
        myState.lastPath = null;
      }
      if (myState.lastPath != null) {
        File lastFile = new File(myState.lastPath);
        if (lastFile.isDirectory() && !new File(lastFile, Project.DIRECTORY_STORE_FOLDER).exists()) {
          myState.lastPath = null;
        }
      }
    }
  }

  public void removePath(final String path) {
    if (path == null) return;
    synchronized (myStateLock) {
      if (SystemInfo.isFileSystemCaseSensitive) {
        myState.removePath(path);
      }
      else {
        for (String p : ArrayUtil.toStringArray(myState.recentPaths)) {
          if (path.equalsIgnoreCase(p)) {
            myState.removePath(path);
          }
        }
      }
    }
  }

  public String getLastProjectPath() {
    synchronized (myStateLock) {
      return myState.lastPath;
    }
  }

  public void updateLastProjectPath() {
    final Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    synchronized (myStateLock) {
      if (openProjects.length == 0) {
        myState.lastPath = null;
        myState.openPaths = Collections.emptyList();
      }
      else {
        myState.lastPath = getProjectPath(openProjects[openProjects.length - 1]);
        myState.openPaths = ContainerUtil.newArrayList();
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

  private Set<String> getDuplicateProjectNames(Set<String> openedPaths, Set<String> recentPaths) {
    Set<String> names = ContainerUtil.newHashSet();
    Set<String> duplicates = ContainerUtil.newHashSet();
    for (String path : ContainerUtil.concat(openedPaths, recentPaths)) {
      if (!names.add(getProjectName(path))) {
        duplicates.add(path);
      }
    }
    return duplicates;
  }

  /**
   * @param addClearListItem whether the "Clear List" action should be added to the end of the list.
   */
  public AnAction[] getRecentProjectsActions(boolean addClearListItem) {
    final Set<String> paths;
    synchronized (myStateLock) {
      myState.validateRecentProjects();
      paths = ContainerUtil.newLinkedHashSet(myState.recentPaths);
    }

    final Set<String> openedPaths = ContainerUtil.newHashSet();
    for (Project openProject : ProjectManager.getInstance().getOpenProjects()) {
      ContainerUtil.addIfNotNull(openedPaths, getProjectPath(openProject));
    }

    paths.remove(null);
    paths.removeAll(openedPaths);

    List<AnAction> actions = new ArrayList<AnAction>();
    Set<String> duplicates = getDuplicateProjectNames(openedPaths, paths);
    for (final String path : paths) {
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
      if (new File(path).exists()) {
        actions.add(new ReopenProjectAction(path, projectName, displayName));
      }
    }

    if (actions.isEmpty()) {
      return AnAction.EMPTY_ARRAY;
    }

    if (addClearListItem) {
      AnAction clearListAction = new DumbAwareAction(IdeBundle.message("action.clear.list")) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          String message = IdeBundle.message("action.clear.list.message");
          String title = IdeBundle.message("action.clear.list.title");
          if (Messages.showOkCancelDialog(e.getProject(), message, title, Messages.getQuestionIcon()) == Messages.OK) {
            synchronized (myStateLock) {
              myState.recentPaths.clear();
            }
            WelcomeFrame.clearRecents();
          }
        }
      };
      
      actions.add(Separator.getInstance());
      actions.add(clearListAction);
    }

    return actions.toArray(new AnAction[actions.size()]);
  }

  private void markPathRecent(String path) {
    synchronized (myStateLock) {
      myState.lastPath = path;
      removePath(path);
      myState.recentPaths.add(0, path);
    }
  }

  @Nullable
  protected abstract String getProjectPath(@NotNull Project project);

  protected abstract void doOpenProject(@NotNull String projectPath, @Nullable Project projectToClose, boolean forceOpenInNewFrame);

  public static boolean isValidProjectPath(String projectPath) {
    final File file = new File(projectPath);
    return file.exists() && (!file.isDirectory() || new File(file, Project.DIRECTORY_STORE_FOLDER).exists());
  }

  @Override
  public void projectOpened(final Project project) {
    String path = getProjectPath(project);
    if (path != null) {
      markPathRecent(path);
    }
    SystemDock.updateMenu();
  }

  @Override
  public final boolean canCloseProject(Project project) {
    return true;
  }

  @Override
  public void projectClosing(Project project) {
    synchronized (myStateLock) {
      myState.names.put(getProjectPath(project), getProjectDisplayName(project));
    }
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
    SystemDock.updateMenu();
  }

  @NotNull
  private String getProjectName(String path) {
    String cached = myNameCache.get(path);
    if (cached != null) {
      return cached;
    }
    String result = readProjectName(path);
    myNameCache.put(path, result);
    return result;
  }

  public void clearNameCache() {
    myNameCache.clear();
  }

  private static String readProjectName(String path) {
    final File file = new File(path);
    if (file.isDirectory()) {
      final File nameFile = new File(new File(path, Project.DIRECTORY_STORE_FOLDER), ProjectImpl.NAME_FILE);
      if (nameFile.exists()) {
        try {
          final BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(nameFile), "UTF-8"));
          try {
            final String name = in.readLine();
            if (name != null && name.length() > 0) return name.trim();
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
      return FileUtil.getNameWithoutExtension(file.getName());
    }
  }

  protected boolean willReopenProjectOnStart() {
    return GeneralSettings.getInstance().isReopenLastProject() && getLastProjectPath() != null;
  }

  protected void doReopenLastProject() {
    GeneralSettings generalSettings = GeneralSettings.getInstance();
    if (generalSettings.isReopenLastProject()) {
      Collection<String> openPaths;
      synchronized (myStateLock) {
        openPaths = ContainerUtil.newLinkedHashSet(myState.openPaths);
      }
      if (!openPaths.isEmpty()) {
        for (String openPath : openPaths) {
          if (isValidProjectPath(openPath)) {
            doOpenProject(openPath, null, true);
          }
        }
      }
      else {
        String lastProjectPath = getLastProjectPath();
        if (lastProjectPath != null) {
          if (isValidProjectPath(lastProjectPath)) doOpenProject(lastProjectPath, null, false);
        }
      }
    }
  }

  private class MyAppLifecycleListener extends AppLifecycleListener.Adapter {
    @Override
    public void appFrameCreated(final String[] commandLineArgs, @NotNull final Ref<Boolean> willOpenProject) {
      if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
        ProjectManager.getInstance().addProjectManagerListener(RecentProjectsManagerBase.this);
      }
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
}
