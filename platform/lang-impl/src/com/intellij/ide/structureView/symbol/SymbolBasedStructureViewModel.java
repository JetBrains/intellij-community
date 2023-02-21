// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.structureView.symbol;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.TextEditorBasedStructureViewModel;
import com.intellij.model.psi.PsiSymbolDeclaration;
import com.intellij.model.psi.impl.Declarations;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public abstract class SymbolBasedStructureViewModel extends TextEditorBasedStructureViewModel {
  protected SymbolBasedStructureViewModel(@NotNull PsiFile psiFile) {
    super(psiFile);
  }

  protected SymbolBasedStructureViewModel(Editor editor) {
    super(editor);
  }

  protected SymbolBasedStructureViewModel(Editor editor, PsiFile file) {
    super(editor, file);
  }

  protected boolean shouldCreateNode(@NotNull PsiSymbolDeclaration symbolDeclaration) {
    return true;
  }

  @NotNull
  public ArrayList<StructureViewTreeElement> collectClosestChildrenSymbols(@NotNull PsiElement rootElement) {
    var result = new ArrayList<StructureViewTreeElement>();
    PsiElementVisitor visitor = new PsiElementVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        var elementDeclarations = Declarations.allDeclarationsInElement(element);
        if (elementDeclarations.isEmpty()) {
          ProgressIndicatorProvider.checkCanceled();
          element.acceptChildren(this);
        }
        else {
          elementDeclarations.forEach(it -> {
            if (shouldCreateNode(it)) {
              result.add(new PsiSymbolTreeElement(it) {
                @Override
                public @NotNull Collection<StructureViewTreeElement> getChildrenBase() {
                  final PsiElement element = getElement();
                  return element != null ? collectClosestChildrenSymbols(element) : Collections.emptyList();
                }
              });
            }
          });
        }
      }
    };
    rootElement.acceptChildren(visitor);
    return result;
  }
}
