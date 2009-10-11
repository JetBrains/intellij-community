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

package com.intellij.refactoring.rename;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.util.containers.HashSet;

import java.util.Set;

/**
 * @author dsl
 */
public class RenameHandlerRegistry {
  private final Set<RenameHandler> myHandlers  = new HashSet<RenameHandler>();
  private static final RenameHandlerRegistry INSTANCE = new RenameHandlerRegistry();
  private final PsiElementRenameHandler myDefaultElementRenameHandler;

  public static RenameHandlerRegistry getInstance() {
    return INSTANCE;
  }

  private RenameHandlerRegistry() {
    // should be checked last
    myDefaultElementRenameHandler = new PsiElementRenameHandler();
  }

  public boolean hasAvailableHandler(DataContext dataContext) {
    for (RenameHandler renameHandler : Extensions.getExtensions(RenameHandler.EP_NAME)) {
      if (renameHandler.isAvailableOnDataContext(dataContext)) return true;
    }
    for (RenameHandler renameHandler : myHandlers) {
      if (renameHandler.isAvailableOnDataContext(dataContext)) return true;
    }
    return myDefaultElementRenameHandler.isAvailableOnDataContext(dataContext);
  }

  public RenameHandler getRenameHandler(DataContext dataContext) {
    for (RenameHandler renameHandler : Extensions.getExtensions(RenameHandler.EP_NAME)) {
      if (renameHandler.isRenaming(dataContext)) return renameHandler;
    }
    for (RenameHandler renameHandler : myHandlers) {
      if (renameHandler.isRenaming(dataContext)) return renameHandler;
    }
    return myDefaultElementRenameHandler.isRenaming(dataContext) ? myDefaultElementRenameHandler : null;
  }

  public void registerHandler(RenameHandler handler) {
    myHandlers.add(handler);
  }
}
