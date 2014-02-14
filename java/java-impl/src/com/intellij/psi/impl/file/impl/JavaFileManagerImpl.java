/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

package com.intellij.psi.impl.file.impl;

import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.util.messages.MessageBus;

/**
 * @author max
 */
public class JavaFileManagerImpl extends JavaFileManagerBase {

  public JavaFileManagerImpl(final PsiManagerEx manager, final ProjectRootManager projectRootManager, MessageBus bus,
                             final StartupManager startupManager) {
    super(manager, projectRootManager, bus);

    if (!((StartupManagerEx)startupManager).startupActivityPassed() && 
        !ApplicationManager.getApplication().isUnitTestMode() && 
        !manager.getProject().isDefault()) {
      Logger.getInstance("#com.intellij.psi.impl.file.impl.JavaFileManagerImpl")
        .error("Access to psi files should be performed only after startup activity");
    }
  }
}
