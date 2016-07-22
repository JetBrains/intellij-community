/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ide;

import com.intellij.openapi.diagnostic.Logger;
import sun.awt.DisplayChangedListener;

import java.awt.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class DisplayChangeDetector {
  private static final Logger LOG = Logger.getInstance(DisplayChangeDetector.class);
  private static final DisplayChangeDetector INSTANCE = new DisplayChangeDetector();
  
  public static DisplayChangeDetector getInstance() {
    return INSTANCE;
  }

  @SuppressWarnings("FieldCanBeLocal") // we need to keep a strong reference to this listener, as GraphicsEnvironment keeps only weak references to them
  private final DisplayChangeHandler myHandler = new DisplayChangeHandler();
  private final List<Listener> myListeners = new CopyOnWriteArrayList<>();

  private DisplayChangeDetector() {
    try {
      GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
      env.getScreenDevices();    // init
      Class<?> aClass = Class.forName("sun.awt.DisplayChangedListener"); // might be absent

      if (aClass.isInstance(env)) { // Headless env does not implement sun.awt.DisplayChangedListener (and lacks addDisplayChangedListener)
        env.getClass()
          .getMethod("addDisplayChangedListener", new Class[]{aClass})
          .invoke(env, myHandler);
      }
    }
    catch (HeadlessException ignored) {}
    catch (Throwable t) {
      LOG.error("Cannot setup display change listener", t);
    }
  }

  public void addListener(Listener listener) {
    myListeners.add(listener);
  }

  public void removeListener(Listener listener) {
    myListeners.remove(listener);
  }

  public interface Listener {
    void displayChanged();
  }
  
  private class DisplayChangeHandler implements DisplayChangedListener {
    public void displayChanged() {
      runActions();
    }

    public void paletteChanged() {
      runActions();
    }

    private void runActions() {
      for (Listener listener : myListeners) {
        listener.displayChanged();
      }
    }
  }
}
