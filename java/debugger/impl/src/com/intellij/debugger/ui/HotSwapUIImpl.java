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
package com.intellij.debugger.ui;

import com.intellij.CommonBundle;
import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerManager;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.impl.DebuggerManagerAdapter;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.HotSwapFile;
import com.intellij.debugger.impl.HotSwapManager;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.compiler.CompilationStatusListener;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.compiler.CompilerTopics;
import com.intellij.openapi.compiler.ex.CompilerPathsEx;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.PairFunction;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.util.JpsPathUtil;

import javax.swing.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * User: lex
 * Date: Oct 2, 2003
 * Time: 6:00:55 PM
 */
public class HotSwapUIImpl extends HotSwapUI implements ProjectComponent {
  private final List<HotSwapVetoableListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private boolean myAskBeforeHotswap = true;
  private final Project myProject;
  private boolean myPerformHotswapAfterThisCompilation = true;

  public HotSwapUIImpl(final Project project, final MessageBus bus, DebuggerManager debugManager) {
    myProject = project;

    ((DebuggerManagerEx)debugManager).addDebuggerManagerListener(new DebuggerManagerAdapter() {
      private MessageBusConnection myConn = null;

      @Override
      public void sessionAttached(DebuggerSession session) {
        if (myConn == null) {
          myConn = bus.connect();
          myConn.subscribe(CompilerTopics.COMPILATION_STATUS, new MyCompilationStatusListener());
        }
      }

      @Override
      public void sessionDetached(DebuggerSession session) {
        if (!getHotSwappableDebugSessions().isEmpty()) return;

        final MessageBusConnection conn = myConn;
        if (conn != null) {
          Disposer.dispose(conn);
          myConn = null;
        }
      }
    });
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  @NotNull
  public String getComponentName() {
    return "HotSwapUI";
  }

  public void initComponent() {
  }

  public void disposeComponent() {

  }

  public void addListener(HotSwapVetoableListener listener) {
    myListeners.add(listener);
  }

  public void removeListener(HotSwapVetoableListener listener) {
    myListeners.remove(listener);
  }

  private static boolean shouldDisplayHangWarning(DebuggerSettings settings, List<DebuggerSession> sessions) {
    if (!settings.HOTSWAP_HANG_WARNING_ENABLED) {
      return false;
    }
    // todo: return false if yourkit agent is inactive
    for (DebuggerSession session : sessions) {
      if (session.isPaused()) {
        return true;
      }
    }
    return false;
  }

  private void hotSwapSessions(final List<DebuggerSession> sessions, @Nullable final Map<String, List<String>> generatedPaths) {
    final boolean shouldAskBeforeHotswap = myAskBeforeHotswap;
    myAskBeforeHotswap = true;

    // need this because search with PSI is perormed during hotswap
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    final DebuggerSettings settings = DebuggerSettings.getInstance();
    final String runHotswap = settings.RUN_HOTSWAP_AFTER_COMPILE;
    final boolean shouldDisplayHangWarning = shouldDisplayHangWarning(settings, sessions);

    if (shouldAskBeforeHotswap && DebuggerSettings.RUN_HOTSWAP_NEVER.equals(runHotswap)) {
      return;
    }

    final boolean shouldPerformScan = generatedPaths == null;

    final HotSwapProgressImpl findClassesProgress;
    if (shouldPerformScan) {
      findClassesProgress = new HotSwapProgressImpl(myProject);
    }
    else {
      boolean createProgress = false;
      for (DebuggerSession session : sessions) {
        if (session.isModifiedClassesScanRequired()) {
          createProgress = true;
          break;
        }
      }
      findClassesProgress = createProgress ? new HotSwapProgressImpl(myProject) : null;
    }

    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        final Map<DebuggerSession, Map<String, HotSwapFile>> modifiedClasses;
        if (shouldPerformScan) {
          modifiedClasses = scanForModifiedClassesWithProgress(sessions, findClassesProgress, false);
        }
        else {
          final List<DebuggerSession> toScan = new ArrayList<>();
          final List<DebuggerSession> toUseGenerated = new ArrayList<>();
          for (DebuggerSession session : sessions) {
            (session.isModifiedClassesScanRequired() ? toScan : toUseGenerated).add(session);
            session.setModifiedClassesScanRequired(false);
          }
          modifiedClasses = new HashMap<>();
          if (!toUseGenerated.isEmpty()) {
            modifiedClasses.putAll(HotSwapManager.findModifiedClasses(toUseGenerated, generatedPaths));
          }
          if (!toScan.isEmpty()) {
            modifiedClasses.putAll(scanForModifiedClassesWithProgress(toScan, findClassesProgress, !true));
          }
        }

        final Application application = ApplicationManager.getApplication();
        if (modifiedClasses.isEmpty()) {
          final String message = DebuggerBundle.message("status.hotswap.uptodate");
          HotSwapProgressImpl.NOTIFICATION_GROUP.createNotification(message, NotificationType.INFORMATION).notify(myProject);
          return;
        }

        application.invokeLater(new Runnable() {
          public void run() {
            if (shouldAskBeforeHotswap && !DebuggerSettings.RUN_HOTSWAP_ALWAYS.equals(runHotswap)) {
              final RunHotswapDialog dialog = new RunHotswapDialog(myProject, sessions, shouldDisplayHangWarning);
              if (!dialog.showAndGet()) {
                for (DebuggerSession session : modifiedClasses.keySet()) {
                  session.setModifiedClassesScanRequired(true);
                }
                return;
              }
              final Set<DebuggerSession> toReload = new HashSet<>(dialog.getSessionsToReload());
              for (DebuggerSession session : modifiedClasses.keySet()) {
                if (!toReload.contains(session)) {
                  session.setModifiedClassesScanRequired(true);
                }
              }
              modifiedClasses.keySet().retainAll(toReload);
            }
            else {
              if (shouldDisplayHangWarning) {
                final int answer = Messages.showCheckboxMessageDialog(
                  DebuggerBundle.message("hotswap.dialog.hang.warning"),
                  DebuggerBundle.message("hotswap.dialog.title"),
                  new String[]{"Perform &Reload Classes", "&Skip Reload Classes"},
                  CommonBundle.message("dialog.options.do.not.show"),
                  false, 1, 1, Messages.getWarningIcon(),
                  new PairFunction<Integer, JCheckBox, Integer>() {
                    @Override
                    public Integer fun(Integer exitCode, JCheckBox cb) {
                      settings.HOTSWAP_HANG_WARNING_ENABLED = !cb.isSelected();
                      return exitCode == DialogWrapper.OK_EXIT_CODE ? exitCode : DialogWrapper.CANCEL_EXIT_CODE;
                    }
                  }
                );
                if (answer == DialogWrapper.CANCEL_EXIT_CODE) {
                  for (DebuggerSession session : modifiedClasses.keySet()) {
                    session.setModifiedClassesScanRequired(true);
                  }
                  return;
                }
              }
            }

            if (!modifiedClasses.isEmpty()) {
              final HotSwapProgressImpl progress = new HotSwapProgressImpl(myProject);
              application.executeOnPooledThread(new Runnable() {
                public void run() {
                  reloadModifiedClasses(modifiedClasses, progress);
                }
              });
            }
          }
        }, ModalityState.NON_MODAL);
      }
    });
  }

  private static Map<DebuggerSession, Map<String, HotSwapFile>> scanForModifiedClassesWithProgress(final List<DebuggerSession> sessions,
                                                                                                   final HotSwapProgressImpl progress,
                                                                                                   final boolean scanWithVFS) {
    final Ref<Map<DebuggerSession, Map<String, HotSwapFile>>> result = Ref.create(null);
    ProgressManager.getInstance().runProcess(new Runnable() {
      public void run() {
        try {
          result.set(HotSwapManager.scanForModifiedClasses(sessions, progress, scanWithVFS));
        }
        finally {
          progress.finished();
        }
      }
    }, progress.getProgressIndicator());
    return result.get();
  }

  private static void reloadModifiedClasses(final Map<DebuggerSession, Map<String, HotSwapFile>> modifiedClasses, final HotSwapProgressImpl progress) {
    ProgressManager.getInstance().runProcess(new Runnable() {
      public void run() {
        HotSwapManager.reloadModifiedClasses(modifiedClasses, progress);
        progress.finished();
      }
    }, progress.getProgressIndicator());
  }

  public void reloadChangedClasses(final DebuggerSession session, boolean compileBeforeHotswap) {
    dontAskHotswapAfterThisCompilation();
    if (compileBeforeHotswap) {
      CompilerManager.getInstance(session.getProject()).make(null);
    }
    else {
      if (session.isAttached()) {
        hotSwapSessions(Collections.singletonList(session), null);
      }
    }
  }

  public void dontPerformHotswapAfterThisCompilation() {
    myPerformHotswapAfterThisCompilation = false;
  }

  public void dontAskHotswapAfterThisCompilation() {
    myAskBeforeHotswap = false;
  }

  private class MyCompilationStatusListener implements CompilationStatusListener {

    private final AtomicReference<Map<String, List<String>>> myGeneratedPaths = new AtomicReference<>(new HashMap<>());
    private final THashSet<File> myOutputRoots;

    private MyCompilationStatusListener() {
      myOutputRoots = new THashSet<>(FileUtil.FILE_HASHING_STRATEGY);
      for (final String path : CompilerPathsEx.getOutputPaths(ModuleManager.getInstance(myProject).getModules())) {
        myOutputRoots.add(new File(path));
      }
    }

    public void fileGenerated(String outputRoot, String relativePath) {
      if (StringUtil.endsWith(relativePath, ".class") && JpsPathUtil.isUnder(myOutputRoots, new File(outputRoot))) {
        // collect only classes
        final Map<String, List<String>> map = myGeneratedPaths.get();
        List<String> paths = map.get(outputRoot);
        if (paths == null) {
          paths = new ArrayList<>();
          map.put(outputRoot, paths);
        }
        paths.add(relativePath);
      }
    }

    public void compilationFinished(boolean aborted, int errors, int warnings, CompileContext compileContext) {
      final Map<String, List<String>> generated = myGeneratedPaths.getAndSet(new HashMap<>());
      if (myProject.isDisposed()) {
        return;
      }

      if (errors == 0 && !aborted && myPerformHotswapAfterThisCompilation) {
        for (HotSwapVetoableListener listener : myListeners) {
          if (!listener.shouldHotSwap(compileContext)) {
            return;
          }
        }

        List<DebuggerSession> sessions = getHotSwappableDebugSessions();
        if (!sessions.isEmpty()) {
          hotSwapSessions(sessions, generated);
        }
      }
      myPerformHotswapAfterThisCompilation = true;
    }
  }

  public static boolean canHotSwap(@NotNull DebuggerSession debuggerSession) {
    return debuggerSession.isAttached() && debuggerSession.getProcess().canRedefineClasses();
  }

  @NotNull
  private List<DebuggerSession> getHotSwappableDebugSessions() {
    return DebuggerManagerEx.getInstanceEx(myProject).getSessions().stream()
      .filter(HotSwapUIImpl::canHotSwap)
      .collect(Collectors.toCollection(SmartList::new));
  }
}
