// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

import static com.intellij.util.ObjectUtils.coalesce;
import static com.intellij.util.containers.ContainerUtil.*;

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

  @Override
  public Object getCurrentEditorElement() {
    if (!(super.getCurrentEditorElement() instanceof PsiElement element)) {
      return null;
    }

    final Collection<PsiSymbolDeclaration> declarations = filter(Declarations.allDeclarationsInElement(element), this::shouldCreateNode);
    if (declarations.isEmpty()) {
      return element;
    }

    final Editor editor = getEditor();
    if (editor == null) {
      return element;
    }

    final int offset = editor.getCaretModel().getOffset();

    final PsiSymbolDeclaration declaration =
      coalesce(find(declarations, it -> it.getAbsoluteRange().containsOffset(offset)), getFirstItem(declarations));

    return new DelegatingPsiElementWithSymbolPointer(element, declaration.getSymbol().createPointer());
  }

  protected boolean shouldCreateNode(@NotNull PsiSymbolDeclaration symbolDeclaration) {
    return true;
  }

  public @NotNull ArrayList<StructureViewTreeElement> collectClosestChildrenSymbols(@NotNull PsiElement rootElement) {
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
