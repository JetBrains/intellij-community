/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.codeInsight.intention.impl.config;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionBean;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.openapi.actionSystem.ShortcutProvider;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IntentionActionWrapper implements IntentionAction, ShortcutProvider, IntentionActionDelegate {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.config.IntentionActionWrapper");

  private IntentionAction myDelegate;
  private final String[] myCategories;
  private final IntentionActionBean myExtension;
  private String myFullFamilyName;

  IntentionActionWrapper(@NotNull IntentionActionBean extension, String[] categories) {
    myExtension = extension;
    myCategories = categories;
  }

  @Override
  @NotNull
  public String getText() {
    return getDelegate().getText();
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return getDelegate().getFamilyName();
  }

  @Override
  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    return getDelegate().isAvailable(project, editor, file);
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    getDelegate().invoke(project, editor, file);
  }

  @Override
  public boolean startInWriteAction() {
    return getDelegate().startInWriteAction();
  }

  @Nullable
  @Override
  public PsiElement getElementToMakeWritable(@NotNull PsiFile file) {
    return getDelegate().getElementToMakeWritable(file);
  }

  @NotNull
  public String getFullFamilyName(){
    String result = myFullFamilyName;
    if (result == null) {
      myFullFamilyName = result = myCategories != null ? StringUtil.join(myCategories, "/") + "/" + getFamilyName() : getFamilyName();
    }
    return result;
  }

  @NotNull
  @Override
  public synchronized IntentionAction getDelegate() {
    if (myDelegate == null) {
      try {
        myDelegate = myExtension.instantiate();
      }
      catch (ClassNotFoundException e) {
        LOG.error(e);
      }
    }
    return myDelegate;
  }

  public String getImplementationClassName() {
    return myExtension.className;
  }

  @NotNull
  ClassLoader getImplementationClassLoader() {
    return myExtension.getLoaderForClass();
  }

  @Override
  public String toString() {
    String text;
    try {
      text = getText();
    }
    catch (PsiInvalidElementAccessException e) {
      text = e.getMessage();
    }
    return "Intention: (" + getDelegate().getClass() + "): '" + text + "'";
  }

  @Override
  public boolean equals(Object obj) {
    return super.equals(obj) || getDelegate().equals(obj);
  }

  @Nullable
  @Override
  public ShortcutSet getShortcut() {
    IntentionAction delegate = getDelegate();
    return delegate instanceof ShortcutProvider ? ((ShortcutProvider)delegate).getShortcut() : null;
  }
}
