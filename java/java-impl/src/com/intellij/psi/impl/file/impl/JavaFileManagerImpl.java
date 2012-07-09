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

import com.intellij.AppTopics;
import com.intellij.openapi.fileEditor.FileDocumentManagerAdapter;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class JavaFileManagerImpl extends JavaFileManagerBase {


  public JavaFileManagerImpl(final PsiManagerEx manager, final ProjectRootManager projectRootManager, MessageBus bus,
                             final StartupManager startupManager) {
    super(manager, projectRootManager, bus);

    myConnection.subscribe(AppTopics.FILE_DOCUMENT_SYNC, new FileDocumentManagerAdapter() {
      @Override
      public void fileWithNoDocumentChanged(@NotNull final VirtualFile file) {
        clearNonRepositoryMaps();
      }
    });
    


    startupManager.registerStartupActivity(
      new Runnable() {
        @Override
        public void run() {
          initialize();
        }
      }
    );

  }
}
