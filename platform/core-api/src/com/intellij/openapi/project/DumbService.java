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
package com.intellij.openapi.project;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.NotNullLazyKey;
import com.intellij.openapi.util.Ref;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * A service managing IDEA's 'dumb' mode: when indices are updated in background and the functionality is very much limited.
 * Only the explicitly allowed functionality is available. Usually it's allowed by implementing {@link com.intellij.openapi.project.DumbAware} interface.
 *
 * If you want to register a toolwindow, which will be enabled during the dumb mode, please use {@link com.intellij.openapi.wm.ToolWindowManager}'s
 * registration methods which have 'canWorkInDumMode' parameter. 
 *
 * @author peter
 */
public abstract class DumbService {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.project.DumbService");

  /**
   * @see com.intellij.openapi.project.Project#getMessageBus()
   */
  public static final Topic<DumbModeListener> DUMB_MODE = new Topic<DumbModeListener>("dumb mode", DumbModeListener.class);

  /**
   * @return whether IntelliJ IDEA is in dumb mode, which means that right now indices are updated in background.
   * IDEA offers only limited functionality at such times, e.g. plain text file editing and version control operations.
   */
  public abstract boolean isDumb();

  public static boolean isDumb(Project project) {
    return getInstance(project).isDumb();
  }

  /**
   * Executes the runnable immediately if not in dumb mode, or on AWT Event Dispatch thread when the dumb mode ends.
   * @param runnable runnable to run
   */
  public abstract void runWhenSmart(Runnable runnable);

  /**
   * Pause the current thread until dumb mode ends and then continue execution.
   * NOTE: there are no guarantees that a new dumb mode won't begin before the next statement.
   * Hence: use with care. Consider using {@link #runWhenSmart(Runnable)}, {@link #runReadActionInSmartMode(Runnable)} or {@link #repeatUntilPassesInSmartMode(Runnable)} instead
   */
  public abstract void waitForSmartMode();

  /**
   * Pause the current thread until dumb mode ends, and then run the read action. Index is guaranteed to be available inside that read action.
   */
  public <T> T runReadActionInSmartMode(final Computable<T> r) {
    final Ref<T> result = new Ref<T>();
    runReadActionInSmartMode(new Runnable() {
      @Override
      public void run() {
        result.set(r.compute());
      }
    });
    return result.get();
  }

  /**
   * Pause the current thread until dumb mode ends, and then run the read action. Index is guaranteed to be available inside that read action.
   */
  public void runReadActionInSmartMode(final Runnable r) {
    while (true) {
      waitForSmartMode();
      boolean success = ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
        @Override
        public Boolean compute() {
          if (isDumb()) {
            return false;
          }
          r.run();
          return true;
        }
      });
      if (success) break;
    }
  }

  /**
   * Pause the current thread until dumb mode ends, and then attempt to execute the runnable. If it fails due to another dumb mode having started,
   * try again until the runnable is able to complete successfully.
   * It makes sense to use this method when you have a long-running activity consisting of many small read actions, and you don't want to
   * use a single long read action in order to keep the IDE responsive.
   * 
   * @see #runReadActionInSmartMode(Runnable) 
   */
  public void repeatUntilPassesInSmartMode(final Runnable r) {
    while (true) {
      waitForSmartMode();
      try {
        r.run();
        return;
      }
      catch (IndexNotReadyException e) {
        LOG.info(e);
      }
    }
  }

  /**
   * Invoke the runnable later on EventDispatchThread AND when IDEA isn't in dumb mode
   * @param runnable runnable
   */
  public void smartInvokeLater(@NotNull final Runnable runnable) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        runWhenSmart(runnable);
      }
    });
  }

  public void smartInvokeLater(@NotNull final Runnable runnable, ModalityState modalityState) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        runWhenSmart(runnable);
      }
    }, modalityState);
  }

  private static final NotNullLazyKey<DumbService, Project> INSTANCE_KEY = ServiceManager.createLazyKey(DumbService.class);

  public static DumbService getInstance(@NotNull Project project) {
    return INSTANCE_KEY.getValue(project);
  }

  @NotNull
  public <T> List<T> filterByDumbAwareness(@NotNull Collection<T> collection) {
    if (isDumb()) {
      final ArrayList<T> result = new ArrayList<T>(collection);
      for (Iterator<T> iterator = result.iterator(); iterator.hasNext();) {
        if (!isDumbAware(iterator.next())) {
          iterator.remove();
        }
      }
      return result;
    }

    if (collection instanceof List) {
      return (List<T>)collection;
    }

    return new ArrayList<T>(collection);
  }

  public abstract JComponent wrapGently(@NotNull JComponent dumbUnawareContent, @NotNull Disposable parentDisposable);

  public void makeDumbAware(@NotNull final JComponent component, @NotNull Disposable disposable) {
    component.setEnabled(!isDumb());
    getProject().getMessageBus().connect(disposable).subscribe(DUMB_MODE, new DumbModeListener() {
      @Override
      public void enteredDumbMode() {
        component.setEnabled(false);
      }

      @Override
      public void exitDumbMode() {
        component.setEnabled(true);
      }
    });
  }

  public abstract void showDumbModeNotification(String message);

  public abstract Project getProject();

  public static boolean isDumbAware(Object o) {
    if (o instanceof PossiblyDumbAware) {
      return ((PossiblyDumbAware)o).isDumbAware();
    }
    return o instanceof DumbAware;
  }

  /**
   * @see #DUMB_MODE
   */
  public interface DumbModeListener {

    /**
     * The event arrives on EDT
     */
    void enteredDumbMode();

    /**
     * The event arrives on EDT
     */
    void exitDumbMode();

  }
}
