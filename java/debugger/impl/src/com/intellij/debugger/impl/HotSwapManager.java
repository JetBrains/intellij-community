// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.impl;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsCollectorImpl;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

@Service
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

    List<String> paths = outputPaths != null ? outputPaths.getValue() :
                         ReadAction.compute(() -> JBIterable.of(OrderEnumerator.orderEntries(session.getProject()).classes().getRoots())
                           .filterMap(o -> o.isDirectory() && !o.getFileSystem().isReadOnly() ? o.getPath() : null)
                           .toList()
                         );
    for (String path : paths) {
      String rootPath = FileUtil.toCanonicalPath(path);
      collectModifiedClasses(new File(path), rootPath, rootPath + "/", modifiedClasses, progress, timeStamp);
    }

    return modifiedClasses;
  }

  private static boolean collectModifiedClasses(File file, String filePath, String rootPath, Map<String, HotSwapFile> container, HotSwapProgress progress, long timeStamp) {
    if (progress.isCancelled()) {
      return false;
    }
    final File[] files = file.listFiles();
    if (files != null) {
      for (File child : files) {
        if (!collectModifiedClasses(child, filePath + "/" + child.getName(), rootPath, container, progress, timeStamp)) {
          return false;
        }
      }
    }
    else { // not a dir
      if (SystemInfo.isFileSystemCaseSensitive? StringUtil.endsWith(filePath, CLASS_EXTENSION) : StringUtil.endsWithIgnoreCase(filePath, CLASS_EXTENSION)) {
        if (file.lastModified() > timeStamp) {
          progress.setText(JavaDebuggerBundle.message("progress.hotswap.scanning.path", filePath));
          final String qualifiedName = filePath.substring(rootPath.length(), filePath.length() - CLASS_EXTENSION.length()).replace('/', '.');
          container.put(qualifiedName, new HotSwapFile(file));
        }
      }
    }
    return true;
  }

  private static HotSwapManager getInstance(Project project) {
    return project.getService(HotSwapManager.class);
  }

  private void reloadClasses(DebuggerSession session, Map<String, HotSwapFile> classesToReload, HotSwapProgress progress) {
    final long newSwapTime = System.currentTimeMillis();
    new ReloadClassesWorker(session, progress).reloadClasses(classesToReload);
    if (progress.isCancelled()) {
      session.setModifiedClassesScanRequired(true);
    }
    else {
      setTimeStamp(session, newSwapTime);
    }
  }

  public static Map<DebuggerSession, Map<String, HotSwapFile>> findModifiedClasses(List<DebuggerSession> sessions, Map<String, Collection<String>> generatedPaths) {
    final Map<DebuggerSession, Map<String, HotSwapFile>> result = new HashMap<>();
    List<Pair<DebuggerSession, Long>> sessionWithStamps = new ArrayList<>();
    for (DebuggerSession session : sessions) {
      sessionWithStamps.add(new Pair<>(session, getInstance(session.getProject()).getTimeStamp(session)));
    }
    for (Map.Entry<String, Collection<String>> entry : generatedPaths.entrySet()) {
      final File root = new File(entry.getKey());
      for (String relativePath : entry.getValue()) {
        if (SystemInfo.isFileSystemCaseSensitive? StringUtil.endsWith(relativePath, CLASS_EXTENSION) : StringUtil.endsWithIgnoreCase(relativePath, CLASS_EXTENSION)) {
          final String qualifiedName = relativePath.substring(0, relativePath.length() - CLASS_EXTENSION.length()).replace('/', '.');
          final HotSwapFile hotswapFile = new HotSwapFile(new File(root, relativePath));
          final long fileStamp = hotswapFile.file.lastModified();

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

  @NotNull
  public static Map<DebuggerSession, Map<String, HotSwapFile>> scanForModifiedClasses(@NotNull List<DebuggerSession> sessions,
                                                                                      @NotNull HotSwapProgress swapProgress) {
    return scanForModifiedClasses(sessions, null, swapProgress);
  }


  @NotNull
  public static Map<DebuggerSession, Map<String, HotSwapFile>> scanForModifiedClasses(@NotNull List<DebuggerSession> sessions,
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

  public static void reloadModifiedClasses(@NotNull Map<DebuggerSession, Map<String, HotSwapFile>> modifiedClasses, @NotNull HotSwapProgress reloadClassesProgress) {
    MultiProcessCommand reloadClassesCommand = new MultiProcessCommand();
    reloadClassesProgress.setCancelWorker(() -> reloadClassesCommand.cancel());
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
