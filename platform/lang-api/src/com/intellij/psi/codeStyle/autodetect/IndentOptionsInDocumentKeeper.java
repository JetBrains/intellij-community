/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle.autodetect;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.FileIndentOptionsProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IndentOptionsInDocumentKeeper {

  public static final Key<Pair<CommonCodeStyleSettings.IndentOptions, FileIndentOptionsProvider>> INDENT_OPTIONS_KEY = Key.create("INDENT_OPTIONS_KEY");

  public static void storeIntoDocument(@NotNull Document document,
                                       @NotNull CommonCodeStyleSettings.IndentOptions options,
                                       @Nullable FileIndentOptionsProvider provider) {
    document.putUserData(INDENT_OPTIONS_KEY, Pair.create(options, provider));
  }

  @Nullable
  public static Pair<CommonCodeStyleSettings.IndentOptions, FileIndentOptionsProvider> retrieveFromUnderlyingDocument(@NotNull PsiFile file) {
    Project project = file.getProject();
    Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    return document != null ? retrieveFromDocument(document) : null;
  }

  public static Pair<CommonCodeStyleSettings.IndentOptions, FileIndentOptionsProvider> retrieveFromDocument(@NotNull Document document) {
    return document.getUserData(INDENT_OPTIONS_KEY);
  }
}
