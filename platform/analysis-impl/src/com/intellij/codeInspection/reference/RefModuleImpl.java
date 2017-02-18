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
package com.intellij.codeInspection.reference;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;

/**
 * User: anna
 * Date: 09-Jan-2006
 */
class RefModuleImpl extends RefEntityImpl implements RefModule {
  private final Module myModule;

  RefModuleImpl(@NotNull Module module, @NotNull RefManager manager) {
    super(module.getName(), manager);
    myModule = module;
    ((RefProjectImpl)manager.getRefProject()).add(this);
  }

  @Override
  public synchronized void add(@NotNull final RefEntity child) {
    if (myChildren == null) {
      myChildren = new ArrayList<>();
    }
    myChildren.add(child);

    if (child.getOwner() == null) {
      ((RefEntityImpl)child).setOwner(this);
    }
  }

  @Override
  protected synchronized void removeChild(@NotNull final RefEntity child) {
    if (myChildren != null) {
      myChildren.remove(child);
    }
  }

  @Override
  public void accept(@NotNull final RefVisitor refVisitor) {
    ApplicationManager.getApplication().runReadAction(() -> refVisitor.visitModule(this));
  }

  @Override
  @NotNull
  public Module getModule() {
    return myModule;
  }

  @Override
  public boolean isValid() {
    return !myModule.isDisposed();
  }

  @Override
  public Icon getIcon(final boolean expanded) {
    return PlatformIcons.CLOSED_MODULE_GROUP_ICON; //ModuleType.get(getModule()).getIcon();
  }

  @Nullable
  static RefEntity moduleFromName(final RefManager manager, final String name) {
    return manager.getRefModule(ModuleManager.getInstance(manager.getProject()).findModuleByName(name));
  }
}
