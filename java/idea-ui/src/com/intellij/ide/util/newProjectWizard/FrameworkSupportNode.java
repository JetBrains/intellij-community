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
package com.intellij.ide.util.newProjectWizard;

import com.intellij.framework.addSupport.FrameworkSupportInModuleConfigurable;
import com.intellij.framework.addSupport.FrameworkSupportInModuleProvider;
import com.intellij.ide.util.newProjectWizard.impl.FrameworkSupportModelBase;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
* @author nik
*/
public class FrameworkSupportNode extends FrameworkSupportNodeBase {
  private final FrameworkSupportInModuleProvider myProvider;
  private FrameworkSupportInModuleConfigurable myConfigurable;
  private final FrameworkSupportModelBase myModel;
  private final Disposable myParentDisposable;

  public FrameworkSupportNode(final FrameworkSupportInModuleProvider provider, final FrameworkSupportNodeBase parentNode, final FrameworkSupportModelBase model,
                              Disposable parentDisposable) {
    super(provider, parentNode);
    myParentDisposable = parentDisposable;
    myProvider = provider;
    model.registerComponent(provider, this);
    myModel = model;
  }

  public FrameworkSupportInModuleProvider getProvider() {
    return myProvider;
  }

  public synchronized FrameworkSupportInModuleConfigurable getConfigurable() {
    if (myConfigurable == null) {
      myConfigurable = myProvider.createConfigurable(myModel);
      Disposer.register(myParentDisposable, myConfigurable);
    }
    return myConfigurable;
  }

  @NotNull
  public String getTitle() {
    return myProvider.getPresentableName();
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return myProvider.getFrameworkType().getIcon();
  }

  @NotNull
  @Override
  public String getId() {
    return myProvider.getFrameworkType().getId();
  }
}
