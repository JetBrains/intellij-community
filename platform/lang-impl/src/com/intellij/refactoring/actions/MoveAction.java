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

package com.intellij.refactoring.actions;

import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.move.MoveHandler;

public class MoveAction extends BaseRefactoringAction {
  @Deprecated public static final String MOVE_PROVIDER = MoveProvider.DATA_KEY.getName();

  private final MoveProvider myDefaultMoveProvider = new MoveProvider() {
    public boolean isEnabledOnDataContext(DataContext dataContext) {
      return false;
    }

    public RefactoringActionHandler getHandler(DataContext dataContext) {
      return new MoveHandler();
    }
  };

  public MoveAction() {
    setInjectedContext(true);
  }

  public boolean isAvailableInEditorOnly() {
    return false;
  }

  protected boolean isAvailableForLanguage(Language language){
    // move is supported in any language
    return true;
  }

  public boolean isEnabledOnElements(PsiElement[] elements) {
    return MoveHandler.canMove(elements, null);
  }

  protected boolean isEnabledOnDataContext(DataContext dataContext) {
    return getMoveProvider(dataContext).isEnabledOnDataContext(dataContext);
  }

  private MoveProvider getMoveProvider(DataContext dataContext) {
    final MoveProvider moveProvider = MoveProvider.DATA_KEY.getData(dataContext);
    if (moveProvider != null) {
      return moveProvider;
    }

    return myDefaultMoveProvider;
  }

  public RefactoringActionHandler getHandler(DataContext dataContext) {
    return getMoveProvider(dataContext).getHandler(dataContext);
  }

  public interface MoveProvider {
    DataKey<MoveProvider> DATA_KEY = DataKey.create("MoveProvider");

    boolean isEnabledOnDataContext(DataContext dataContext);

    RefactoringActionHandler getHandler(DataContext dataContext);
  }
}