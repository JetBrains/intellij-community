/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ide.dnd;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;

import javax.swing.*;
import java.awt.*;

public abstract class DnDManager {
  public static DnDManager getInstance() {
    Application application = ApplicationManager.getApplication();
    return application != null ? (DnDManager)application.getPicoContainer().getComponentInstance(DnDManager.class.getName()) : null;
  }

  public abstract void registerSource(DnDSource source, JComponent component);

  public abstract void registerSource(AdvancedDnDSource source);

  public abstract void unregisterSource(DnDSource source, JComponent component);

  public abstract void unregisterSource(AdvancedDnDSource source);

  public abstract void registerTarget(DnDTarget target, JComponent component);

  public abstract void unregisterTarget(DnDTarget target, JComponent component);

  public abstract Component getLastDropHandler();
}
