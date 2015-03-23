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

package com.intellij.codeInsight.template;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public abstract class TemplateContextType {
  public static final ExtensionPointName<TemplateContextType> EP_NAME = ExtensionPointName.create("com.intellij.liveTemplateContext");

  @NotNull
  private final String myContextId;
  @NotNull
  private final String myPresentableName;
  private final Class<? extends TemplateContextType> myBaseContextType;

  protected TemplateContextType(@NotNull @NonNls String id, @NotNull String presentableName) {
    this(id, presentableName, EverywhereContextType.class);
  }

  protected TemplateContextType(@NotNull @NonNls String id,
                                @NotNull String presentableName,
                                @Nullable Class<? extends TemplateContextType> baseContextType) {
    myContextId = id;
    myPresentableName = presentableName;
    myBaseContextType = baseContextType;
  }

  @NotNull
  public String getPresentableName() {
    return myPresentableName;
  }

  @NotNull
  public String getContextId() {
    return myContextId;
  }

  public abstract boolean isInContext(@NotNull PsiFile file, int offset);

  /**
   * @return whether an abbreviation of this context's template can be entered in editor
   * and expanded from there by Insert Live Template action
   */
  public boolean isExpandableFromEditor() {
    return true;
  }

  @Nullable
  public SyntaxHighlighter createHighlighter() {
    return null;
  }

  @Nullable
  public TemplateContextType getBaseContextType() {
    return myBaseContextType != null ? EP_NAME.findExtension(myBaseContextType) : null;
  }

  public Document createDocument(CharSequence text, Project project) {
    return EditorFactory.getInstance().createDocument(text);
  }
}
