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
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.ui.popup.BalloonHandler;
import com.intellij.openapi.util.NotNullLazyKey;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  /**
   * @see com.intellij.openapi.project.Project#getMessageBus()
   */
  public static final Topic<DumbModeListener> DUMB_MODE = new Topic<DumbModeListener>("dumb mode", DumbModeListener.class);

  /**
   * @return whether IntelliJ IDEA is in dumb mode, which means that right now indices are updated in background.
   * IDEA offers only limited functionality at such times, e.g. plain text file editing and version control operations.
   */
  public abstract boolean isDumb();

  /**
   * Run the runnable when dumb mode ends
   * @param runnable runnable to run
   */
  public abstract void runWhenSmart(Runnable runnable);

  public abstract void waitForSmartMode();

  /**
   * Invoke the runnable later on EventDispatchThread AND when IDEA isn't in dumb mode
   * @param runnable runnable
   */
  public void smartInvokeLater(@NotNull final Runnable runnable) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        runWhenSmart(runnable);
      }
    });
  }

  public void smartInvokeLater(@NotNull final Runnable runnable, ModalityState modalityState) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        runWhenSmart(runnable);
      }
    }, modalityState);
  }

  private static final NotNullLazyKey<DumbService, Project> INSTANCE_KEY = ServiceManager.createLazyKey(DumbService.class);

  public static DumbService getInstance(@NotNull Project project) {
    return INSTANCE_KEY.getValue(project);
  }

  public <T> List<T> filterByDumbAwareness(@Nullable Collection<T> collection) {
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

  public DumbUnawareHider wrapGently(@NotNull JComponent dumbUnawareContent, @NotNull Disposable parentDisposable) {
    final DumbUnawareHider wrapper = new DumbUnawareHider(dumbUnawareContent);
    wrapper.setContentVisible(!isDumb());
    getProject().getMessageBus().connect(parentDisposable).subscribe(DUMB_MODE, new DumbModeListener() {

      public void enteredDumbMode() {
        wrapper.setContentVisible(false);
      }

      public void exitDumbMode() {
        wrapper.setContentVisible(true);
      }
    });

    return wrapper;
  }

  public void makeDumbAware(@NotNull final JComponent component, @NotNull Disposable disposable) {
    component.setEnabled(!isDumb());
    getProject().getMessageBus().connect(disposable).subscribe(DUMB_MODE, new DumbModeListener() {
      public void enteredDumbMode() {
        component.setEnabled(false);
      }

      public void exitDumbMode() {
        component.setEnabled(true);
      }
    });
  }

  public abstract BalloonHandler showDumbModeNotification(String message);

  public abstract Project getProject();

  public static boolean isDumbAware(Object o) {
    if (o instanceof AnAction) {
      return ((AnAction)o).isDumbAware();
    } else {
      return o instanceof DumbAware;
    }
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
