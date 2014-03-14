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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.impl.ProjectLifecycleListener;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.Function;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class IconDeferrerImpl extends IconDeferrer {
  private final Object LOCK = new Object();
  private final Map<Object, Icon> myIconsCache = new LinkedHashMap<Object, Icon>() {
    @Override
    protected boolean removeEldestEntry(Map.Entry<Object, Icon> eldest) {
      return size() > 100;
    }
  };
  private long myLastClearTimestamp = 0;
  @SuppressWarnings("UnusedDeclaration") private final LowMemoryWatcher myLowMemoryWatcher = LowMemoryWatcher.register(new Runnable() {
    @Override
    public void run() {
      clear();
    }
  });

  public IconDeferrerImpl(MessageBus bus) {
    final MessageBusConnection connection = bus.connect();
    connection.subscribe(PsiModificationTracker.TOPIC, new PsiModificationTracker.Listener() {
      @Override
      public void modificationCountChanged() {
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
      myLastClearTimestamp++;
    }
  }

  @Override
  public <T> Icon defer(final Icon base, final T param, @NotNull final Function<T, Icon> f) {
    if (myEvaluationIsInProgress.get().booleanValue()) {
      return f.fun(param);
    }

    synchronized (LOCK) {
      Icon result = myIconsCache.get(param);
      if (result == null) {
        final long started = myLastClearTimestamp;
        result = new DeferredIconImpl<T>(base, param, f).setDoneListener(new DeferredIconImpl.IconListener<T>() {
          @Override
          public void evalDone(T key, @NotNull Icon r) {
            synchronized (LOCK) {
              // check if our results is not outdated yet
              if (started == myLastClearTimestamp) {
                myIconsCache.put(key, r);
              }
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

  public static void evaluateDeferred(@NotNull Runnable runnable) {
    try {
      myEvaluationIsInProgress.set(Boolean.TRUE);
      runnable.run();
    }
    finally {
      myEvaluationIsInProgress.set(Boolean.FALSE);
    }
  }
}
