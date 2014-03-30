/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.LanguageNamesValidation;
import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ven
 */
public class RenameElementFix implements IntentionAction, LocalQuickFix {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.RenameFileFix");

  private final PsiNamedElement myElement;
  private final String myNewName;
  private final String myText;

  public RenameElementFix(@NotNull PsiNamedElement element) {
    myElement = element;
    final VirtualFile vFile = myElement.getContainingFile().getVirtualFile();
    assert vFile != null : element;
    myNewName = vFile.getNameWithoutExtension();
    myText =  CodeInsightBundle.message("rename.public.class.text", myElement.getName(), myNewName);
  }

  public RenameElementFix(@NotNull PsiNamedElement element, @NotNull String newName) {
    myElement = element;
    myNewName = newName;
    myText = CodeInsightBundle.message("rename.named.element.text", myElement.getName(), myNewName);
  }

  @Override
  @NotNull
  public String getText() {
    return myText;
  }

  @Override
  @NotNull
  public String getName() {
    return getText();
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("rename.element.family");
  }

  @Override
  public void applyFix(@NotNull final Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiFile file = descriptor.getPsiElement().getContainingFile();
    if (isAvailable(project, null, file)) {
      new WriteCommandAction(project) {
        @Override
        protected void run(Result result) throws Throwable {
          invoke(project, FileEditorManager.getInstance(project).getSelectedTextEditor(), file);
        }
      }.execute();
    }
  }

  @Override
  public boolean isAvailable(@NotNull Project project, @Nullable Editor editor, PsiFile file) {
    if (!myElement.isValid()) {
      return false;
    }
    final NamesValidator namesValidator = LanguageNamesValidation.INSTANCE.forLanguage(file.getLanguage());
    return namesValidator != null && namesValidator.isIdentifier(myNewName, project);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    LOG.assertTrue(file == myElement.getContainingFile());
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;
    RenameProcessor processor = new RenameProcessor(project, myElement, myNewName, false, false);
    processor.run();
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
