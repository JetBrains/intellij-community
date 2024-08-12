// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename.inplace;

import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.ide.DataManager;
import com.intellij.lang.LanguageRefactoringSupport;
import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.command.impl.StartMarkAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Pass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

public class MemberInplaceRenameHandler extends VariableInplaceRenameHandler {
  @Override
  protected boolean isAvailable(@Nullable PsiElement element,
                                @NotNull Editor editor,
                                @NotNull PsiFile file) {
    PsiElement nameSuggestionContext = file.findElementAt(editor.getCaretModel().getOffset());
    if (nameSuggestionContext == null && editor.getCaretModel().getOffset() > 0) {
      nameSuggestionContext = file.findElementAt(editor.getCaretModel().getOffset() - 1);
    }

    if (element == null && LookupManager.getActiveLookup(editor) != null) {
      element = PsiTreeUtil.getParentOfType(nameSuggestionContext, PsiNamedElement.class);
    }
    final RefactoringSupportProvider
      supportProvider = element == null ? null : LanguageRefactoringSupport.INSTANCE.forContext(element);
    return editor.getSettings().isVariableInplaceRenameEnabled()
           && supportProvider != null
           && element instanceof PsiNameIdentifierOwner
           && supportProvider.isMemberInplaceRenameAvailable(element, nameSuggestionContext);
  }

  @Override
  public InplaceRefactoring doRename(@NotNull PsiElement elementToRename,
                                     @NotNull Editor editor,
                                     @Nullable DataContext dataContext) {
    Component contextComponent = ObjectUtils.notNull(dataContext != null ? PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(dataContext) : null, editor.getComponent());
    String newName = dataContext != null ? PsiElementRenameHandler.DEFAULT_NAME.getData(dataContext) : null;
    PsiElement newElementToRename = null;
    if (elementToRename instanceof PsiNameIdentifierOwner) {
      final RenamePsiElementProcessor processor = RenamePsiElementProcessor.forElement(elementToRename);
      if (processor.isInplaceRenameSupported()) {
        final StartMarkAction startMarkAction = StartMarkAction.canStart(editor);
        if (startMarkAction == null || (newElementToRename = processor.substituteElementToRename(elementToRename, editor)) == elementToRename) {
          processor.substituteElementToRename(elementToRename, editor, new Pass<>() {
            @Override
            public void pass(PsiElement element) {
              final MemberInplaceRenamer renamer = createMemberRenamer(element, (PsiNameIdentifierOwner)elementToRename, editor);
              List<String> names = dataContext == null ? null : PsiElementRenameHandler.NAME_SUGGESTIONS.getData(dataContext);
              boolean startedRename = renamer.performInplaceRename(names);
              if (!startedRename) {
                performDialogRename(elementToRename, editor, createDataContext(contextComponent, newName, elementToRename), renamer.myInitialName);
              }
            }
          });
          return null;
        }
        else {
          final InplaceRefactoring inplaceRefactoring = editor.getUserData(InplaceRefactoring.INPLACE_RENAMER);
          if (inplaceRefactoring != null && inplaceRefactoring.getClass() == MemberInplaceRenamer.class) {
            final TemplateState templateState = TemplateManagerImpl.getTemplateState(InjectedLanguageEditorUtil.getTopLevelEditor(editor));
            if (templateState != null) {
              templateState.gotoEnd(true);
            }
          }
        }
      }
    }
    performDialogRename(elementToRename, editor, createDataContext(contextComponent, newName, newElementToRename), null);
    return null;
  }

  private static DataContext createDataContext(Component contextComponent, String newName, PsiElement newElementToRename) {
    DataContext context = DataManager.getInstance().getDataContext(contextComponent);
    if (newName == null && newElementToRename == null) return context;
    return SimpleDataContext.builder()
      .setParent(context)
      .add(PsiElementRenameHandler.DEFAULT_NAME, newName)
      .add(LangDataKeys.PSI_ELEMENT_ARRAY, newElementToRename == null ? null : new PsiElement[]{newElementToRename})
      .build();
  }

  protected @NotNull MemberInplaceRenamer createMemberRenamer(@NotNull PsiElement element,
                                                              @NotNull PsiNameIdentifierOwner elementToRename,
                                                              @NotNull Editor editor) {
    return new MemberInplaceRenamer(elementToRename, element, editor);
  }
}