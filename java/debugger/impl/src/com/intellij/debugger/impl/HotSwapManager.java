// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsCollectorImpl;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.impl.hotswap.HotSwapStatistics;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

@Service(Service.Level.PROJECT)
public final class HotSwapManager {
  private final Map<DebuggerSession, Long> myTimeStamps = new HashMap<>();
  private static final String CLASS_EXTENSION = ".class";

  private long getTimeStamp(DebuggerSession session) {
    Long tStamp = myTimeStamps.get(session);
    return tStamp != null ? tStamp.longValue() : 0;
  }

  private void setTimeStamp(DebuggerSession session, long tStamp) {
    myTimeStamps.put(session, tStamp);
  }

  public Map<String, HotSwapFile> scanForModifiedClasses(@NotNull DebuggerSession session,
                                                         @Nullable NotNullLazyValue<? extends List<String>> outputPaths,
                                                         @NotNull HotSwapProgress progress) {
    DebuggerManagerThreadImpl.assertIsManagerThread();

    final long timeStamp = getTimeStamp(session);
    final Map<String, HotSwapFile> modifiedClasses = new HashMap<>();

    List<String> paths = outputPaths != null ? outputPaths.getValue() : getWritableOutputPaths(session);
    for (String path : paths) {
      String rootPath = FileUtil.toCanonicalPath(path);
      collectModifiedClasses(new File(path), rootPath, rootPath.length() + 1, modifiedClasses, progress, timeStamp);
    }

    return modifiedClasses;
  }

  private static @NotNull List<String> getWritableOutputPaths(@NotNull DebuggerSession session) {
    return ReadAction.computeBlocking(() -> ContainerUtil.mapNotNull(
      OrderEnumerator.orderEntries(session.getProject()).classes().getRoots(),
      o -> o.isDirectory() && !o.getFileSystem().isReadOnly() ? o.getPath() : null
    ));
  }

  /** Finds already compiled class files in class roots without applying the session HotSwap timestamp filter. */
  @ApiStatus.Internal
  @SuppressWarnings("IO_FILE_USAGE")
  public static @NotNull Map<String, HotSwapClassFile> findExistingClassesForHotSwap(@NotNull DebuggerSession session,
                                                                                     @NotNull Collection<String> qualifiedNames,
                                                                                     @NotNull HotSwapProgress progress) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if (qualifiedNames.isEmpty() || progress.isCancelled()) {
      return Collections.emptyMap();
    }

    Map<String, HotSwapClassFile> result = new HashMap<>();
    for (VirtualFile root : getClassRoots(session, qualifiedNames)) {
      if (progress.isCancelled()) {
        break;
      }
      if (result.keySet().containsAll(qualifiedNames)) {
        break;
      }

      Path rootPath = root.getFileSystem().getNioPath(root);
      if (rootPath != null && Files.isRegularFile(rootPath)) {
        collectExistingClassesFromArchive(rootPath, root.getPresentableUrl(), qualifiedNames, progress, result);
        continue;
      }

      for (String qualifiedName : qualifiedNames) {
        if (result.containsKey(qualifiedName)) {
          continue;
        }

        String relativePath = getClassFileRelativePath(qualifiedName);
        if (root.isDirectory() && !root.getFileSystem().isReadOnly()) {
          File classFile = new File(root.getPath(), relativePath);
          if (!classFile.isFile()) {
            continue;
          }
          progress.setText(JavaDebuggerBundle.message("progress.hotswap.scanning.path", classFile.getPath()));
          result.put(qualifiedName, new HotSwapFile(classFile));
          continue;
        }

        VirtualFile classFile = root.findFileByRelativePath(relativePath);
        if (classFile == null || classFile.isDirectory()) {
          continue;
        }

        HotSwapClassFile hotSwapFile = createHotSwapFile(classFile);
        if (hotSwapFile == null) {
          continue;
        }

        progress.setText(JavaDebuggerBundle.message("progress.hotswap.scanning.path", classFile.getPresentableUrl()));
        result.put(qualifiedName, hotSwapFile);
      }
    }
    return result;
  }

  private static @NotNull List<VirtualFile> getClassRoots(@NotNull DebuggerSession session, @NotNull Collection<String> qualifiedNames) {
    return ReadAction.computeBlocking(() -> {
      Project project = session.getProject();
      LinkedHashSet<VirtualFile> roots = new LinkedHashSet<>();
      JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
      GlobalSearchScope searchScope = session.getSearchScope();
      GlobalSearchScope allScope = GlobalSearchScope.allScope(project);
      ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);
      for (String qualifiedName : qualifiedNames) {
        PsiClass psiClass = findClassForHotSwap(psiFacade, qualifiedName, searchScope, allScope);
        if (psiClass == null) {
          continue;
        }

        PsiFile containingFile = psiClass.getContainingFile();
        VirtualFile virtualFile = containingFile != null ? containingFile.getVirtualFile() : null;
        if (virtualFile == null) {
          continue;
        }

        Module module = fileIndex.getModuleForFile(virtualFile);
        if (module == null) {
          continue;
        }

        Collections.addAll(roots, getClassRoots(module));
      }
      Collections.addAll(roots, OrderEnumerator.orderEntries(project).classes().getRoots());
      return new ArrayList<>(roots);
    });
  }

  private static VirtualFile @NotNull [] getClassRoots(@NotNull Module module) {
    return OrderEnumerator.orderEntries(module).withoutSdk().withoutDepModules().classes().getRoots();
  }

  private static @Nullable PsiClass findClassForHotSwap(@NotNull JavaPsiFacade psiFacade,
                                                        @NotNull String qualifiedName,
                                                        @NotNull GlobalSearchScope searchScope,
                                                        @NotNull GlobalSearchScope allScope) {
    String sourceName = qualifiedName.replace('$', '.');
    PsiClass psiClass = psiFacade.findClass(sourceName, searchScope);
    if (psiClass != null) return psiClass;

    psiClass = psiFacade.findClass(sourceName, allScope);
    if (psiClass != null) return psiClass;

    int innerClassSeparator = qualifiedName.indexOf('$');
    if (innerClassSeparator < 0) return null;

    String topLevelName = qualifiedName.substring(0, innerClassSeparator);
    psiClass = psiFacade.findClass(topLevelName, searchScope);
    if (psiClass != null) return psiClass;

    return psiFacade.findClass(topLevelName, allScope);
  }

  private static @Nullable HotSwapClassFile createHotSwapFile(@NotNull VirtualFile classFile) {
    Path file = classFile.getFileSystem().getNioPath(classFile);
    if (file != null && !Files.isRegularFile(file)) {
      return null;
    }
    return HotSwapClassFile.fromVirtualFile(classFile);
  }

  @SuppressWarnings("IO_FILE_USAGE")
  private static void collectExistingClassesFromArchive(@NotNull Path archive,
                                                        @NotNull String presentableUrl,
                                                        @NotNull Collection<String> qualifiedNames,
                                                        @NotNull HotSwapProgress progress,
                                                        @NotNull Map<String, HotSwapClassFile> result) {
    try (JarFile jarFile = new JarFile(archive.toFile())) {
      for (String qualifiedName : qualifiedNames) {
        if (progress.isCancelled()) {
          return;
        }
        if (result.containsKey(qualifiedName)) {
          continue;
        }

        String relativePath = getClassFileRelativePath(qualifiedName);
        HotSwapClassFile hotSwapFile = createHotSwapFile(jarFile, relativePath);
        if (hotSwapFile == null) {
          continue;
        }

        progress.setText(JavaDebuggerBundle.message("progress.hotswap.scanning.path", presentableUrl + "!/" + relativePath));
        result.put(qualifiedName, hotSwapFile);
      }
    }
    catch (IOException ignored) {
    }
  }

  private static @Nullable HotSwapClassFile createHotSwapFile(@NotNull JarFile jarFile, @NotNull String relativePath) throws IOException {
    JarEntry entry = jarFile.getJarEntry(relativePath);
    if (entry == null || entry.isDirectory()) {
      return null;
    }
    byte[] bytes;
    try (InputStream inputStream = jarFile.getInputStream(entry)) {
      bytes = inputStream.readAllBytes();
    }
    return HotSwapClassFile.fromBytes(bytes);
  }

  private static @NotNull String getClassFileRelativePath(@NotNull String qualifiedName) {
    return qualifiedName.replace('.', '/') + CLASS_EXTENSION;
  }

  private static boolean collectModifiedClasses(
    File file, String filePath, int rootPathLength, Map<String, HotSwapFile> container, HotSwapProgress progress, long timeStamp
  ) {
    if (progress.isCancelled()) {
      return false;
    }
    final File[] files = file.listFiles();
    if (files != null) {
      for (File child : files) {
        if (!collectModifiedClasses(child, filePath + "/" + child.getName(), rootPathLength, container, progress, timeStamp)) {
          return false;
        }
      }
    }
    else { // not a dir
      String qualifiedName = getQualifiedName(filePath, rootPathLength);
      if (qualifiedName != null && file.lastModified() > timeStamp) {
        progress.setText(JavaDebuggerBundle.message("progress.hotswap.scanning.path", filePath));
        container.put(qualifiedName, new HotSwapFile(file));
      }
    }
    return true;
  }

  private static HotSwapManager getInstance(Project project) {
    return project.getService(HotSwapManager.class);
  }

  private void reloadClasses(DebuggerSession session, Map<String, ? extends HotSwapClassFile> classesToReload, HotSwapProgress progress) {
    final long newSwapTime = System.currentTimeMillis();
    new ReloadClassesWorker(session, progress).reloadClasses(classesToReload);
    if (progress.isCancelled()) {
      session.setModifiedClassesScanRequired(true);
    }
    else {
      setTimeStamp(session, newSwapTime);
    }
  }

  /**
   * Redefines selected already compiled classes without treating them as newly compiled modified classes.
   * Does not update the session HotSwap timestamp or force the next HotSwap to do a full modified-class scan.
   */
  @ApiStatus.Internal
  public static void reloadExistingClasses(@NotNull DebuggerSession session,
                                           @NotNull Map<String, HotSwapClassFile> classesToReload,
                                           @NotNull HotSwapProgress progress) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if (classesToReload.isEmpty()) {
      return;
    }
    progress.setDebuggerSession(session);
    new ReloadClassesWorker(session, progress).reloadClasses(classesToReload);
  }

  public static Map<DebuggerSession, Map<String, HotSwapFile>> findModifiedClasses(
    List<DebuggerSession> sessions, Map<String, Collection<String>> generatedPaths
  ) {
    final Map<DebuggerSession, Map<String, HotSwapFile>> result = new HashMap<>();
    List<Pair<DebuggerSession, Long>> sessionWithStamps = new ArrayList<>();
    for (DebuggerSession session : sessions) {
      sessionWithStamps.add(new Pair<>(session, getInstance(session.getProject()).getTimeStamp(session)));
    }
    for (Map.Entry<String, Collection<String>> entry : generatedPaths.entrySet()) {
      final File root = new File(entry.getKey());
      for (String relativePath : entry.getValue()) {
        String qualifiedName = getQualifiedName(relativePath, 0);
        if (qualifiedName != null) {
          final HotSwapFile hotswapFile = new HotSwapFile(new File(root, relativePath));
          final long fileStamp = hotswapFile.lastModified();

          for (Pair<DebuggerSession, Long> pair : sessionWithStamps) {
            final DebuggerSession session = pair.first;
            if (fileStamp > pair.second) {
              result.computeIfAbsent(session, k -> new HashMap<>()).put(qualifiedName, hotswapFile);
            }
          }
        }
      }
    }
    return result;
  }

  private static String getQualifiedName(String filePath, int rootPathLength) {
    boolean isClassFile = SystemInfo.isFileSystemCaseSensitive
                          ? StringUtil.endsWith(filePath, CLASS_EXTENSION)
                          : StringUtil.endsWithIgnoreCase(filePath, CLASS_EXTENSION);
    if (!isClassFile) return null;
    String withoutExtension = filePath.substring(rootPathLength, filePath.length() - CLASS_EXTENSION.length());
    return withoutExtension.replace('/', '.');
  }

  public static @NotNull Map<DebuggerSession, Map<String, HotSwapFile>> scanForModifiedClasses(@NotNull List<DebuggerSession> sessions,
                                                                                               @NotNull HotSwapProgress swapProgress) {
    return scanForModifiedClasses(sessions, null, swapProgress);
  }


  public static @NotNull Map<DebuggerSession, Map<String, HotSwapFile>> scanForModifiedClasses(@NotNull List<DebuggerSession> sessions,
                                                                                               @Nullable NotNullLazyValue<? extends List<String>> outputPaths,
                                                                                               @NotNull HotSwapProgress swapProgress) {
    final Map<DebuggerSession, Map<String, HotSwapFile>> modifiedClasses = new HashMap<>();
    final MultiProcessCommand scanClassesCommand = new MultiProcessCommand();
    swapProgress.setCancelWorker(() -> scanClassesCommand.cancel());
    for (DebuggerSession debuggerSession : sessions) {
      if (!debuggerSession.isAttached()) {
        continue;
      }

      scanClassesCommand.addCommand(debuggerSession.getProcess(), new DebuggerCommandImpl() {
        @Override
        protected void action() {
          swapProgress.setDebuggerSession(debuggerSession);
          Map<String, HotSwapFile> sessionClasses =
            getInstance(swapProgress.getProject()).scanForModifiedClasses(debuggerSession, outputPaths, swapProgress);
          if (!sessionClasses.isEmpty()) {
            modifiedClasses.put(debuggerSession, sessionClasses);
          }
        }
      });
    }

    swapProgress.setTitle(JavaDebuggerBundle.message("progress.hotswap.scanning.classes"));
    scanClassesCommand.run();

    if (swapProgress.isCancelled()) {
      for (DebuggerSession session : sessions) {
        session.setModifiedClassesScanRequired(true);
      }
      return Collections.emptyMap();
    }
    else {
      return modifiedClasses;
    }
  }

  public static void reloadModifiedClasses(@NotNull Map<DebuggerSession, Map<String, HotSwapFile>> modifiedClasses,
                                           @NotNull HotSwapProgress reloadClassesProgress) {
    MultiProcessCommand reloadClassesCommand = new MultiProcessCommand();
    reloadClassesProgress.setCancelWorker(() -> reloadClassesCommand.cancel());
    int totalClasses = modifiedClasses.values().stream().mapToInt(e -> e.size()).sum();
    HotSwapStatistics.logClassesReloaded(reloadClassesProgress.getProject(), totalClasses);
    for (DebuggerSession debuggerSession : modifiedClasses.keySet()) {
      reloadClassesCommand.addCommand(debuggerSession.getProcess(), new DebuggerCommandImpl() {
        @Override
        protected void action() {
          reloadClassesProgress.setDebuggerSession(debuggerSession);
          getInstance(reloadClassesProgress.getProject()).reloadClasses(
            debuggerSession, modifiedClasses.get(debuggerSession), reloadClassesProgress
          );
        }

        @Override
        protected void commandCancelled() {
          debuggerSession.setModifiedClassesScanRequired(true);
        }
      });
    }

    reloadClassesProgress.setTitle(JavaDebuggerBundle.message("progress.hotswap.reloading"));
    reloadClassesCommand.run();
    ActionsCollectorImpl.recordCustomActionInvoked(reloadClassesProgress.getProject(), "Reload Classes", null, HotSwapManager.class);
  }

  public static class HotSwapDebuggerManagerListener implements DebuggerManagerListener {
    private final Project myProject;

    public HotSwapDebuggerManagerListener(Project project) {
      myProject = project;
    }

    @Override
    public void sessionCreated(DebuggerSession session) {
      getInstance(myProject).setTimeStamp(session, System.currentTimeMillis());
    }

    @Override
    public void sessionRemoved(DebuggerSession session) {
      getInstance(myProject).myTimeStamps.remove(session);
    }
  }
}
