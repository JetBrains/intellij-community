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
package com.intellij.codeInsight.lookup;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceCodeFragment;
import com.intellij.psi.PsiPackage;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
* @author peter
*/
public class PackageLookupItem extends LookupElement {
  private final PsiPackage myPackage;
  private final String myString;
  private final boolean myAddDot;

  public PackageLookupItem(@NotNull PsiPackage aPackage) {
    this(aPackage, null);
  }

  public PackageLookupItem(@NotNull PsiPackage pkg, @Nullable PsiElement context) {
    myPackage = pkg;
    myString = StringUtil.notNullize(myPackage.getName());

    PsiFile file = context == null ? null : context.getContainingFile();
    myAddDot = !(file instanceof PsiJavaCodeReferenceCodeFragment) || ((PsiJavaCodeReferenceCodeFragment)file).isClassesAccepted();
  }

  @NotNull
  @Override
  public Object getObject() {
    return myPackage;
  }

  @NotNull
  @Override
  public String getLookupString() {
    return myString;
  }

  @Override
  public void renderElement(LookupElementPresentation presentation) {
    super.renderElement(presentation);
    if (myAddDot) {
      presentation.setItemText(myString + ".");
    }
    presentation.setIcon(PlatformIcons.PACKAGE_ICON);
  }

  @Override
  public void handleInsert(InsertionContext context) {
    if (myAddDot) {
      context.setAddCompletionChar(false);
      TailType.DOT.processTail(context.getEditor(), context.getTailOffset());
    }
    if (myAddDot || context.getCompletionChar() == '.') {
      AutoPopupController.getInstance(context.getProject()).scheduleAutoPopup(context.getEditor());
    }
  }
}
