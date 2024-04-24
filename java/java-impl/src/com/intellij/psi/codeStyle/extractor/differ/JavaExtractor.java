// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle.extractor.differ;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaCodeFragment;
import com.intellij.psi.JavaCodeFragmentFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.extractor.values.Value;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.codeStyle.CodeFormatterFacade;
import com.intellij.refactoring.RefactorJBundle;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedList;

public final class JavaExtractor implements LangCodeStyleExtractor {
  @Override
  public @NotNull Differ getDiffer(final Project project, PsiFile psiFile, CodeStyleSettings settings) {
    return new DifferBase(project, psiFile, settings) {
      @Override
      public String reformattedText() {
        JavaCodeFragment file = JavaCodeFragmentFactory.getInstance(project).createCodeBlockCodeFragment(myOrigText, myFile, false);

        WriteCommandAction.runWriteCommandAction(myProject, RefactorJBundle.message("codestyle.settings.extractor.command.name"), "CodeStyleSettings extractor", () -> {
          ASTNode treeElement = SourceTreeToPsiMap.psiToTreeNotNull(file);
          SourceTreeToPsiMap.treeElementToPsi(new CodeFormatterFacade(mySettings, file.getLanguage()).processElement(treeElement));
        }, file);

        return file.getText();
      }
    };
  }

  @Override
  public @NotNull Collection<Value.VAR_KIND> getCustomVarKinds() {
    return new LinkedList<>();
  }
}