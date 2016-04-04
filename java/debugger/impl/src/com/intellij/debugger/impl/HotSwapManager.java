/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.debugger.impl;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.ex.CompilerPathsEx;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HotSwapManager extends AbstractProjectComponent {
  private final Map<DebuggerSession, Long> myTimeStamps = new HashMap<>();
  private static final String CLASS_EXTENSION = ".class";

  public HotSwapManager(Project project, DebuggerManagerEx manager) {
    super(project);
    manager.addDebuggerManagerListener(new DebuggerManagerAdapter() {
      public void sessionCreated(DebuggerSession session) {
        myTimeStamps.put(session, Long.valueOf(System.currentTimeMillis()));
      }

      public void sessionRemoved(DebuggerSession session) {
        myTimeStamps.remove(session);
      }
    });
  }

  @NotNull
  public String getComponentName() {
    return "HotSwapManager";
  }

  private long getTimeStamp(DebuggerSession session) {
    Long tStamp = myTimeStamps.get(session);
    return tStamp != null ? tStamp.longValue() : 0;
  }

  void setTimeStamp(DebuggerSession session, long tStamp) {
    myTimeStamps.put(session, Long.valueOf(tStamp));
  }

  public Map<String, HotSwapFile> scanForModifiedClasses(final DebuggerSession session, final HotSwapProgress progress, final boolean scanWithVFS) {
    DebuggerManagerThreadImpl.assertIsManagerThread();

    final long timeStamp = getTimeStamp(session);
    final Map<String, HotSwapFile> modifiedClasses = new HashMap<>();

    if (scanWithVFS) {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          final List<VirtualFile> allDirs = OrderEnumerator.orderEntries(myProject).withoutSdk().withoutLibraries().getPathsList().getRootDirs();
          CompilerPathsEx.visitFiles(allDirs, new CompilerPathsEx.FileVisitor() {
            protected void acceptDirectory(final VirtualFile file, final String fileRoot, final String filePath) {
              if (!progress.isCancelled()) {
                progress.setText(DebuggerBundle.message("progress.hotswap.scanning.path", filePath));
                super.acceptDirectory(file, fileRoot, filePath);
              }
            }

            protected void acceptFile(VirtualFile file, String fileRoot, String filePath) {
              if (progress.isCancelled()) {
                return;
              }
              if (file.getTimeStamp() > timeStamp && StdFileTypes.CLASS.equals(file.getFileType())) {
                //noinspection HardCodedStringLiteral
                if (SystemInfo.isFileSystemCaseSensitive ? filePath.endsWith(CLASS_EXTENSION) : StringUtil.endsWithIgnoreCase(filePath, CLASS_EXTENSION)) {
                  progress.setText(DebuggerBundle.message("progress.hotswap.scanning.path", filePath));
                  //noinspection HardCodedStringLiteral
                  final String qualifiedName = filePath.substring(fileRoot.length() + 1, filePath.length() - CLASS_EXTENSION.length()).replace('/', '.');
                  modifiedClasses.put(qualifiedName, new HotSwapFile(new File(filePath)));
                }
              }
            }
          });
        }
      });
    }
    else {
      final List<File> outputRoots = new ArrayList<>();
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          final List<VirtualFile> allDirs = OrderEnumerator.orderEntries(myProject).withoutSdk().withoutLibraries().getPathsList().getRootDirs();
          for (VirtualFile dir : allDirs) {
            outputRoots.add(new File(dir.getPath()));
          }
        }
      });
      for (File root : outputRoots) {
        final String rootPath = FileUtil.toCanonicalPath(root.getPath());
        collectModifiedClasses(root, rootPath, rootPath + "/", modifiedClasses, progress, timeStamp);
      }
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
          //noinspection HardCodedStringLiteral
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

  public static Map<DebuggerSession, Map<String, HotSwapFile>> findModifiedClasses(List<DebuggerSession> sessions, Map<String, List<String>> generatedPaths) {
    final Map<DebuggerSession, Map<String, HotSwapFile>> result = new java.util.HashMap<>();
    List<Pair<DebuggerSession, Long>> sessionWithStamps = new ArrayList<>();
    for (DebuggerSession session : sessions) {
      sessionWithStamps.add(new Pair<>(session, getInstance(session.getProject()).getTimeStamp(session)));
    }
    for (Map.Entry<String, List<String>> entry : generatedPaths.entrySet()) {
      final File root = new File(entry.getKey());
      for (String relativePath : entry.getValue()) {
        if (SystemInfo.isFileSystemCaseSensitive? StringUtil.endsWith(relativePath, CLASS_EXTENSION) : StringUtil.endsWithIgnoreCase(relativePath, CLASS_EXTENSION)) {
          final String qualifiedName = relativePath.substring(0, relativePath.length() - CLASS_EXTENSION.length()).replace('/', '.');
          final HotSwapFile hotswapFile = new HotSwapFile(new File(root, relativePath));
          final long fileStamp = hotswapFile.file.lastModified();

          for (Pair<DebuggerSession, Long> pair : sessionWithStamps) {
            final DebuggerSession session = pair.first;
            if (fileStamp > pair.second) {
              Map<String, HotSwapFile> container = result.get(session);
              if (container == null) {
                container = new java.util.HashMap<>();
                result.put(session, container);
              }
              container.put(qualifiedName, hotswapFile);
            }
          }
        }
      }
    }
    return result;
  }


  public static Map<DebuggerSession, Map<String, HotSwapFile>> scanForModifiedClasses(final List<DebuggerSession> sessions, final HotSwapProgress swapProgress, final boolean scanWithVFS) {
    final Map<DebuggerSession, Map<String, HotSwapFile>> modifiedClasses = new HashMap<>();

    final MultiProcessCommand scanClassesCommand = new MultiProcessCommand();

    swapProgress.setCancelWorker(new Runnable() {
      public void run() {
        scanClassesCommand.cancel();
      }
    });

    for (final DebuggerSession debuggerSession : sessions) {
      if (debuggerSession.isAttached()) {
                 scanClassesCommand.addCommand(debuggerSession.getProcess(), new DebuggerCommandImpl() {
                   protected void action() throws Exception {
                     swapProgress.setDebuggerSession(debuggerSession);
                     final Map<String, HotSwapFile> sessionClasses =
                       getInstance(swapProgress.getProject()).scanForModifiedClasses(debuggerSession, swapProgress, scanWithVFS);
                     if (!sessionClasses.isEmpty()) {
                       modifiedClasses.put(debuggerSession, sessionClasses);
                     }
                   }
        });
      }
    }

    swapProgress.setTitle(DebuggerBundle.message("progress.hotswap.scanning.classes"));
    scanClassesCommand.run();

    if (swapProgress.isCancelled()) {
      for (DebuggerSession session : sessions) {
        session.setModifiedClassesScanRequired(true);
      }
      return new HashMap<>();
    }
    return modifiedClasses;
  }

  public static void reloadModifiedClasses(final Map<DebuggerSession, Map<String, HotSwapFile>> modifiedClasses, final HotSwapProgress reloadClassesProgress) {
    final MultiProcessCommand reloadClassesCommand = new MultiProcessCommand();

    reloadClassesProgress.setCancelWorker(new Runnable() {
      public void run() {
        reloadClassesCommand.cancel();
      }
    });

    for (final DebuggerSession debuggerSession : modifiedClasses.keySet()) {
      reloadClassesCommand.addCommand(debuggerSession.getProcess(), new DebuggerCommandImpl() {
        protected void action() throws Exception {
          reloadClassesProgress.setDebuggerSession(debuggerSession);
          getInstance(reloadClassesProgress.getProject()).reloadClasses(
            debuggerSession, modifiedClasses.get(debuggerSession), reloadClassesProgress
          );
        }

        protected void commandCancelled() {
          debuggerSession.setModifiedClassesScanRequired(true);
        }
      });
    }

    reloadClassesProgress.setTitle(DebuggerBundle.message("progress.hotswap.reloading"));
    reloadClassesCommand.run();
  }
}
