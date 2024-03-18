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
package com.intellij.psi.codeStyle.extractor.differ;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaCodeFragment;
import com.intellij.psi.JavaCodeFragmentFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.codeStyle.CodeFormatterFacade;
import com.intellij.psi.codeStyle.extractor.values.Value;
import com.intellij.refactoring.RefactorJBundle;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedList;

public final class JavaExtractor implements LangCodeStyleExtractor {
  @NotNull
  @Override
  public Differ getDiffer(final Project project, PsiFile psiFile, CodeStyleSettings settings) {
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

  @NotNull
  @Override
  public Collection<Value.VAR_KIND> getCustomVarKinds() {
    return new LinkedList<>();
  }
}