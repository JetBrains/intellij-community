/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.ide.util.newProjectWizard.impl;

import com.intellij.ide.util.frameworkSupport.FrameworkSupportConfigurable;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportModel;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;

import java.util.List;

/**
 * @author nik
 */
public abstract class FrameworkSupportCommunicator {
  public static final ExtensionPointName<FrameworkSupportCommunicator> EP_NAME = ExtensionPointName.create("com.intellij.frameworkSupportCommunicator");

  public abstract void onFrameworkSupportAdded(Module module, ModifiableRootModel rootModel,
                                               List<FrameworkSupportConfigurable> selectedFrameworks,
                                               FrameworkSupportModel model);
}
