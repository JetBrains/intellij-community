/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.documentation;

import com.intellij.openapi.preview.PreviewPanelProvider;
import com.intellij.openapi.preview.PreviewProviderId;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class DocumentationPreviewPanelProvider extends PreviewPanelProvider<Couple<PsiElement>, DocumentationComponent> {
  public static final PreviewProviderId<Couple<PsiElement>, DocumentationComponent> ID = PreviewProviderId.create("Documentation");
  private final DocumentationComponent myDocumentationComponent;
  private final DocumentationManager myDocumentationManager;

  public DocumentationPreviewPanelProvider(DocumentationManager documentationManager) {
    super(ID);
    myDocumentationManager = documentationManager;
    myDocumentationComponent = new DocumentationComponent(documentationManager) {
      @Override
      public String toString() {
        return "Preview DocumentationComponent (" + (isEmpty() ? "empty" : "not empty") + ")";
      }
    };
  }

  @Override
  public void dispose() {
    Disposer.dispose(myDocumentationComponent);
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myDocumentationComponent;
  }

  @NotNull
  @Override
  protected String getTitle(@NotNull Couple<PsiElement> content) {
    return DocumentationManager.getTitle(content.getFirst(), false);
  }

  @Nullable
  @Override
  protected Icon getIcon(@NotNull Couple<PsiElement> content) {
    return content.getFirst().getIcon(0);
  }

  @Override
  public float getMenuOrder() {
    return 1;
  }

  @Override
  public void showInStandardPlace(@NotNull Couple<PsiElement> content) {
    myDocumentationManager.showJavaDocInfo(content.getFirst(), content.getSecond());
  }

  @Override
  public void release(@NotNull Couple<PsiElement> content) {
  }

  @Override
  public boolean contentsAreEqual(@NotNull Couple<PsiElement> content1, @NotNull Couple<PsiElement> content2) {
    return content1.getFirst().getManager().areElementsEquivalent(content1.getFirst(), content2.getFirst());
  }

  @Override
  public boolean isModified(Couple<PsiElement> content, boolean beforeReuse) {
    return beforeReuse;
  }

  @Override
  protected DocumentationComponent initComponent(Couple<PsiElement> content, boolean requestFocus) {
    if (!content.getFirst().getManager().areElementsEquivalent(myDocumentationComponent.getElement(), content.getFirst())) {
      myDocumentationManager.fetchDocInfo(content.getFirst(), myDocumentationComponent);
    }
    return myDocumentationComponent;
  }
}
