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

/*
 * @author max
 */
package com.intellij.ui;

import com.intellij.ProjectTopics;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.impl.ProjectLifecycleListener;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.Function;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.MessageHandler;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IconDeferrerImpl extends IconDeferrer {
  private final Object LOCK = new Object();
  private final Map<Object, Icon> myIconsCache = new HashMap<Object, Icon>();

  public IconDeferrerImpl(MessageBus bus) {
    final MessageBusConnection connection = bus.connect();
    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      public void before(final List<? extends VFileEvent> events) {
      }

      public void after(final List<? extends VFileEvent> events) {
        clear();
      }
    });
    connection.subscribe(ProjectLifecycleListener.TOPIC, new ProjectLifecycleListener.Adapter() {
      @Override
      public void afterProjectClosed(@NotNull Project project) {
        clear();
      }
    });
  }

  private void clear() {
    synchronized (LOCK) {
      myIconsCache.clear();
    }
  }

  public <T> Icon defer(final Icon base, final T param, final Function<T, Icon> f) {
    if (myEvaluationIsInProgress.get().booleanValue()) {
      return f.fun(param);
    }

    synchronized (LOCK) {
      Icon result = myIconsCache.get(param);
      if (result == null) {
        result = new DeferredIconImpl<T>(base, param, f).setDisposer(new DeferredIconImpl.Disposer<T>() {
          @Override
          public void dispose(T key) {
            synchronized (LOCK) {
              myIconsCache.remove(key);
            }
          }
        });
        myIconsCache.put(param, result);
      }

      return result;
    }
  }

  private static final ThreadLocal<Boolean> myEvaluationIsInProgress = new ThreadLocal<Boolean>() {
    @Override
    protected Boolean initialValue() {
      return Boolean.FALSE;
    }
  };

  public static void evaluateDeferredInReadAction(final Runnable runnable) {
    try {
      myEvaluationIsInProgress.set(Boolean.TRUE);
      ApplicationManager.getApplication().runReadAction(runnable);
    }
    finally {
      myEvaluationIsInProgress.set(Boolean.FALSE);
    }
  }

  public static void evaluateDeferred(final Runnable runnable) {
    try {
      myEvaluationIsInProgress.set(Boolean.TRUE);
      runnable.run();
    }
    finally {
      myEvaluationIsInProgress.set(Boolean.FALSE);
    }
  }
}
