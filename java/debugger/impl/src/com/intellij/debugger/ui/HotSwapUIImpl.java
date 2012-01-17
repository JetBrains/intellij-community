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
package com.intellij.debugger.ui;

import com.intellij.CommonBundle;
import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerManagerEx;
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
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.PairFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * User: lex
 * Date: Oct 2, 2003
 * Time: 6:00:55 PM
 */
public class HotSwapUIImpl extends HotSwapUI implements ProjectComponent{
  private final List<HotSwapVetoableListener> myListeners = ContainerUtil.createEmptyCOWList();
  private boolean myAskBeforeHotswap = true;
  private final Project myProject;
  private boolean myPerformHotswapAfterThisCompilation = true;

  public HotSwapUIImpl(final Project project, MessageBus bus) {
    myProject = project;
    bus.connect().subscribe(CompilerTopics.COMPILATION_STATUS, new CompilationStatusListener() {

      private final AtomicReference<Map<String, List<String>>> myGeneratedPaths = new AtomicReference<Map<String, List<String>>>(new HashMap<String, List<String>>());

      public void fileGenerated(String outputRoot, String relativePath) {
        final Map<String, List<String>> map = myGeneratedPaths.get();
        List<String> paths = map.get(outputRoot);
        if (paths == null) {
          paths = new ArrayList<String>();
          map.put(outputRoot, paths);
        }
        paths.add(relativePath);
      }

      public void compilationFinished(boolean aborted, int errors, int warnings, CompileContext compileContext) {
        final Map<String, List<String>> generated = myGeneratedPaths.getAndSet(new HashMap<String, List<String>>());
        if (myProject.isDisposed()) {
          return;
        }

        if (errors == 0 && !aborted && myPerformHotswapAfterThisCompilation) {
          for (HotSwapVetoableListener listener : myListeners) {
            if (!listener.shouldHotSwap(compileContext)) {
              return;
            }
          }

          final List<DebuggerSession> sessions = new ArrayList<DebuggerSession>();
          Collection<DebuggerSession> debuggerSessions = DebuggerManagerEx.getInstanceEx(myProject).getSessions();
          for (final DebuggerSession debuggerSession : debuggerSessions) {
            if (debuggerSession.isAttached() && debuggerSession.getProcess().canRedefineClasses()) {
              sessions.add(debuggerSession);
            }
          }
          if (!sessions.isEmpty()) {
            hotSwapSessions(sessions, generated);
          }
        }
        myPerformHotswapAfterThisCompilation = true;
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

  private boolean shouldDisplayHangWarning(DebuggerSettings settings, List<DebuggerSession> sessions) {
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

    final HotSwapProgressImpl findClassesProgress = generatedPaths == null? new HotSwapProgressImpl(myProject) : null;
    
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        final Map<DebuggerSession, Map<String, HotSwapFile>> modifiedClasses = generatedPaths == null? scanForModifiedClassesWithProgress(sessions, findClassesProgress) : HotSwapManager.findModifiedClasses(sessions, generatedPaths);

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
              dialog.show();
              if (!dialog.isOK()) {
                return;
              }
              modifiedClasses.keySet().retainAll(dialog.getSessionsToReload());
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

  private static Map<DebuggerSession, Map<String, HotSwapFile>> scanForModifiedClassesWithProgress(final List<DebuggerSession> sessions, final HotSwapProgressImpl progress) {
    final Ref<Map<DebuggerSession, Map<String, HotSwapFile>>> result = Ref.create(null);
    ProgressManager.getInstance().runProcess(new Runnable() {
      public void run() {
        try {
          result.set(HotSwapManager.scanForModifiedClasses(sessions, progress));
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
      if(session.isAttached()) {
        final List<DebuggerSession> sessions = new ArrayList<DebuggerSession>(1);
        sessions.add(session);
        hotSwapSessions(sessions, null);
      }
    }
  }

  public void dontPerformHotswapAfterThisCompilation() {
    myPerformHotswapAfterThisCompilation = false;
  }

  public void dontAskHotswapAfterThisCompilation() {
    myAskBeforeHotswap = false;
  }
}
