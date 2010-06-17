/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectClasspathTraversing;
import com.intellij.openapi.roots.ProjectRootsTraversing;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public class HotSwapManager extends AbstractProjectComponent {
  private final Map<DebuggerSession, Long> myTimeStamps = new HashMap<DebuggerSession, Long>();
  private static final String CLASS_EXTENSION = ".class";

  public HotSwapManager(Project project, DebuggerManagerEx manager) {
    super(project);
    manager.addDebuggerManagerListener(new DebuggerManagerListener() {
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

  public HashMap<String, HotSwapFile> getModifiedClasses(final DebuggerSession session, final HotSwapProgress progress) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    final long timeStamp = getTimeStamp(session);

    final HashMap<String, HotSwapFile> modifiedClasses = new HashMap<String, HotSwapFile>();

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        final List<VirtualFile> allClasses = ProjectRootsTraversing.collectRoots(myProject, ProjectClasspathTraversing.FULL_CLASSPATH_RECURSIVE).getRootDirs();

        final VirtualFile[] allDirs = VfsUtil.toVirtualFileArray(allClasses);
        final FileTypeManager fileTypeManager = FileTypeManager.getInstance();
        CompilerPathsEx.visitFiles(allDirs, new CompilerPathsEx.FileVisitor() {

          protected void acceptDirectory(final VirtualFile file, final String fileRoot, final String filePath) {
            progress.setText(DebuggerBundle.message("progress.hotswap.scanning.path", filePath));
            if(file.getFileSystem() instanceof JarFileSystem && FileTypes.ARCHIVE.equals(fileTypeManager.getFileTypeByFile(file))) {
              if(file.getTimeStamp() > timeStamp) {
                super.acceptDirectory(file, fileRoot, filePath);
              }
            }
            else {
              super.acceptDirectory(file, fileRoot, filePath);
            }
          }

          protected void acceptFile(VirtualFile file, String fileRoot, String filePath) {
            if (file.getTimeStamp() > timeStamp && StdFileTypes.CLASS.equals(fileTypeManager.getFileTypeByFile(file))) {
              //noinspection HardCodedStringLiteral
              if (SystemInfo.isFileSystemCaseSensitive? filePath.endsWith(CLASS_EXTENSION) : StringUtil.endsWithIgnoreCase(filePath, CLASS_EXTENSION)) {
                progress.setText(DebuggerBundle.message("progress.hotswap.scanning.path", filePath));
                //noinspection HardCodedStringLiteral
                final String qualifiedName = filePath.substring(fileRoot.length() + 1, filePath.length() - CLASS_EXTENSION.length()).replace('/', '.');
                modifiedClasses.put(qualifiedName, new HotSwapFile(file));
              }
            }
          }
        });
      }
    });

    return modifiedClasses;
  }

  public static HotSwapManager getInstance(Project project) {
    return project.getComponent(HotSwapManager.class);
  }

  private void reloadClasses(DebuggerSession session, Map<String, HotSwapFile> classesToReload, HotSwapProgress progress) {
    final long newSwapTime = System.currentTimeMillis();
    new ReloadClassesWorker(session, progress).reloadClasses(classesToReload);
    setTimeStamp(session, newSwapTime);
  }

  public static Map<DebuggerSession, Map<String, HotSwapFile>> getModifiedClasses(final List<DebuggerSession> sessions, final HotSwapProgress swapProgress) {
    final Map<DebuggerSession, Map<String, HotSwapFile>> modifiedClasses = new HashMap<DebuggerSession, Map<String, HotSwapFile>>();

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
            HashMap<String, HotSwapFile> sessionClasses =
              getInstance(swapProgress.getProject()).getModifiedClasses(debuggerSession, swapProgress);
            if (!sessionClasses.isEmpty()) {
              modifiedClasses.put(debuggerSession, sessionClasses);
            }
          }
        });
      }
    }

    swapProgress.setTitle(DebuggerBundle.message("progress.hotswap.scanning.classes"));
    scanClassesCommand.run();

    return swapProgress.isCancelled() ? new HashMap<DebuggerSession, Map<String, HotSwapFile>>() : modifiedClasses;
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
      });
    }

    reloadClassesProgress.setTitle(DebuggerBundle.message("progress.hotswap.reloading"));
    reloadClassesCommand.run();
  }
}
