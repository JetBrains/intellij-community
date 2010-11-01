/*
 * Copyright 2000-2010 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.refactoring.changeSignature;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.rename.RenameProcessor;
import org.jetbrains.annotations.NotNull;

/**
* User: anna
* Date: 10/29/10
*/
public abstract class RenameChangeInfo implements ChangeInfo {
  private final PsiNameIdentifierOwner myNamedElement;
  private final String myOldName;

  public RenameChangeInfo(final PsiNameIdentifierOwner namedElement, final ChangeInfo oldInfo) {
    myNamedElement = namedElement;
    myOldName = oldInfo instanceof RenameChangeInfo ? ((RenameChangeInfo)oldInfo).getOldName() : myNamedElement.getName();
  }

  @NotNull
  @Override
  public ParameterInfo[] getNewParameters() {
    return new ParameterInfo[0];
  }

  @Override
  public boolean isParameterSetOrOrderChanged() {
    return false;
  }

  @Override
  public boolean isParameterTypesChanged() {
    return false;
  }

  @Override
  public boolean isParameterNamesChanged() {
    return false;
  }

  @Override
  public boolean isGenerateDelegate() {
    return false;
  }

  @Override
  public boolean isNameChanged() {
    return true;
  }

  @Override
  public PsiElement getMethod() {
    return null;
  }

  @Override
  public boolean isReturnTypeChanged() {
    return false;
  }

  @Override
  public String getNewName() {
    return myNamedElement.getName();
  }

  public String getOldName() {
    return myOldName;
  }

  public PsiNameIdentifierOwner getNamedElement() {
    return myNamedElement;
  }

  public void perform() {
    final String name = myNamedElement.getName();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        myNamedElement.setName(myOldName);
      }
    });
    new RenameProcessor(myNamedElement.getProject(), myNamedElement, name, true, true).run();
  }
}
