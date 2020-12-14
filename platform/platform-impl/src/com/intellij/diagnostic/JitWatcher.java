// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.ide.IdeBundle;
import com.intellij.notification.*;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.help.HelpManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

class JitWatcher {
  private static final Logger LOG = Logger.getInstance(JitWatcher.class);

  private final AtomicBoolean myJitProblemReported = new AtomicBoolean();
  private Method myIsCompilationEnabledMethod;
  private Method myIsCompilationStoppedForeverMethod;
  @NotNull
  private CompilerState myCompilationStateLastValue = CompilerState.STATE_UNKNOWN;

  JitWatcher() {
    // jit compilation check preparations
    try {
      Class<?> clazz = Class.forName("com.jetbrains.management.JitState");

      myIsCompilationEnabledMethod = clazz.getMethod("isCompilationEnabled");
      myIsCompilationEnabledMethod.setAccessible(true);

      myIsCompilationStoppedForeverMethod = clazz.getMethod("isCompilationStoppedForever");
      myIsCompilationStoppedForeverMethod.setAccessible(true);

      myCompilationStateLastValue = getJitCompilerState();
      LOG.info("JIT compilation state checking enabled");
    }
    catch (NoSuchMethodException | ClassNotFoundException e) {
      LOG.debug("Could not enable JIT compilation state checking", e);
    }
  }

  void checkJitState() {
    CompilerState compilationStateCurrentValue = getJitCompilerState();
    if (compilationStateCurrentValue != myCompilationStateLastValue) {
      myCompilationStateLastValue = compilationStateCurrentValue;
      switch (myCompilationStateLastValue) {
        case STATE_UNKNOWN:
          break;
        case DISABLED:
          notifyJitDisabled();
          LOG.warn("The JIT compiler was temporary disabled.");
          break;
        case ENABLED:
          LOG.warn("The JIT compiler was enabled.");
          break;
        case STOPPED_FOREVER:
          notifyJitDisabled();
          LOG.warn("The JIT compiler was stopped forever. This will affect IDE performance.");
          break;
      }
    }
  }

  private void notifyJitDisabled() {
    if (myJitProblemReported.compareAndSet(false, true)) {
      ApplicationEx app = ApplicationManagerEx.getApplicationEx();
      String action = IdeBundle.message(app.isRestartCapable() ? "ide.restart.action" : "ide.shutdown.action");
      String title = IdeBundle.message("notification.title.jit.compiler.disabled");
      String content = IdeBundle.message("notification.content.jit.compiler.disabled");
      NotificationListener listener = new NotificationListener.Adapter() {
        @Override
        protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent e) {
          if ("help".equals(e.getDescription())) {
            HelpManager.getInstance().invokeHelp("Tuning_product_");
          }
        }
      };
      Notification notification = new Notification("PerformanceWatcher", title, content, NotificationType.ERROR, listener).
        addAction(new NotificationAction(action) {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
            notification.expire();
            app.restart(true);
          }
        });
      notification.notify(null);
    }
  }

  @Nullable
  String getJitProblem() {
    if (myCompilationStateLastValue == CompilerState.DISABLED || myCompilationStateLastValue == CompilerState.STOPPED_FOREVER) {
      return "JIT compiler " + myCompilationStateLastValue;
    }
    return null;
  }

  private enum CompilerState {
    STATE_UNKNOWN,
    DISABLED,
    ENABLED,
    STOPPED_FOREVER
  }

  private CompilerState getJitCompilerState() {
    if (myIsCompilationEnabledMethod != null && myIsCompilationStoppedForeverMethod != null) {
      try {
        boolean compilationStateCurrentValue = (Boolean)myIsCompilationEnabledMethod.invoke(null);

        if ((Boolean)myIsCompilationStoppedForeverMethod.invoke(null)) {
          return CompilerState.STOPPED_FOREVER;
        }
        if (compilationStateCurrentValue) {
          return CompilerState.ENABLED;
        }
        return CompilerState.DISABLED;
      }
      catch (IllegalAccessException | InvocationTargetException | IllegalStateException e) {
        LOG.error("Could not perform compilation state check, disabling", e);
        myIsCompilationEnabledMethod = null;
        myIsCompilationStoppedForeverMethod = null;
      }
    }
    return CompilerState.STATE_UNKNOWN;
  }

}
