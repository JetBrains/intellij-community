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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.JavaCodeFragmentFactory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.extractor.values.Value;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.codeStyle.CodeFormatterFacade;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedList;

public class JavaExtractor implements LangCodeStyleExtractor {
  @NotNull
  @Override
  public Differ getDiffer(final Project project, PsiFile psiFile, CodeStyleSettings settings) {
    return new Differ(project, psiFile, settings) {
      @NotNull
      @Override
      public String reformattedText() {
        final Ref<String> res = Ref.create();
        final Ref<PsiFile> file = Ref.create();
        final Ref<ASTNode> node = Ref.create();
        ReadAction.run(
          () -> {
            file.set(createLightCopy());
            node.set(new CodeFormatterFacade(mySettings, file.get().getLanguage()).processElement(SourceTreeToPsiMap.psiElementToTree(file.get())));
          });
        ApplicationManager.getApplication().invokeAndWait(() -> {
          final Document document = file.get().getViewProvider().getDocument();
          if (document != null) {
            PsiDocumentManager.getInstance(project).commitDocument(document);
          }
          res.set(node.get() == null ? "" : node.get().getText());
        });
        return res.get();
      }

      @NotNull
      private PsiFile createLightCopy() {
        return JavaCodeFragmentFactory.getInstance(project).createCodeBlockCodeFragment(myOrigText, myFile, false);
      }
    };
  }

  @NotNull
  @Override
  public Collection<Value.VAR_KIND> getCustomVarKinds() {
    return new LinkedList<>();
  }
}