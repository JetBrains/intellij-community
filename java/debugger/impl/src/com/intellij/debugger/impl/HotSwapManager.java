// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.impl;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.ide.actions.ActionsCollector;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.JBIterable;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

public class HotSwapManager {
  private final Map<DebuggerSession, Long> myTimeStamps = new HashMap<>();
  private static final String CLASS_EXTENSION = ".class";
  private final Project myProject;

  public HotSwapManager(@NotNull Project project) {
    myProject = project;
    project.getMessageBus().connect().subscribe(DebuggerManagerListener.TOPIC, new DebuggerManagerListener() {
      @Override
      public void sessionCreated(DebuggerSession session) {
        myTimeStamps.put(session, Long.valueOf(System.currentTimeMillis()));
      }

      @Override
      public void sessionRemoved(DebuggerSession session) {
        myTimeStamps.remove(session);
      }
    });
  }

  private long getTimeStamp(DebuggerSession session) {
    Long tStamp = myTimeStamps.get(session);
    return tStamp != null ? tStamp.longValue() : 0;
  }

  void setTimeStamp(DebuggerSession session, long tStamp) {
    myTimeStamps.put(session, Long.valueOf(tStamp));
  }

  public Map<String, HotSwapFile> scanForModifiedClasses(@NotNull DebuggerSession session,
                                                         @Nullable NotNullLazyValue<? extends List<String>> outputPaths,
                                                         @NotNull HotSwapProgress progress) {
    DebuggerManagerThreadImpl.assertIsManagerThread();

    final long timeStamp = getTimeStamp(session);
    final Map<String, HotSwapFile> modifiedClasses = new HashMap<>();

    List<String> paths = outputPaths != null ? outputPaths.getValue() :
                         ReadAction.compute(() -> JBIterable.of(OrderEnumerator.orderEntries(myProject).classes().getRoots())
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
          progress.setText(DebuggerBundle.message("progress.hotswap.scanning.path", filePath));
          final String qualifiedName = filePath.substring(rootPath.length(), filePath.length() - CLASS_EXTENSION.length()).replace('/', '.');
          container.put(qualifiedName, new HotSwapFile(file));
        }
      }
    }
    return true;
  }

  public static HotSwapManager getInstance(Project project) {
    return project.getComponent(HotSwapManager.class);
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

  public static Map<DebuggerSession, Map<String, HotSwapFile>> findModifiedClasses(List<? extends DebuggerSession> sessions, Map<String, Collection<String>> generatedPaths) {
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
  public static Map<DebuggerSession, Map<String, HotSwapFile>> scanForModifiedClasses(@NotNull List<? extends DebuggerSession> sessions,
                                                                                      @NotNull HotSwapProgress swapProgress) {
    return scanForModifiedClasses(sessions, null, swapProgress);
  }


  @NotNull
  public static Map<DebuggerSession, Map<String, HotSwapFile>> scanForModifiedClasses(@NotNull List<? extends DebuggerSession> sessions,
                                                                                      @Nullable NotNullLazyValue<? extends List<String>> outputPaths,
                                                                                      @NotNull HotSwapProgress swapProgress) {
    final Map<DebuggerSession, Map<String, HotSwapFile>> modifiedClasses = new THashMap<>();
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

    swapProgress.setTitle(DebuggerBundle.message("progress.hotswap.scanning.classes"));
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

    reloadClassesProgress.setTitle(DebuggerBundle.message("progress.hotswap.reloading"));
    reloadClassesCommand.run();
    ActionsCollector.getInstance().record("Reload Classes", HotSwapManager.class);
  }
}
