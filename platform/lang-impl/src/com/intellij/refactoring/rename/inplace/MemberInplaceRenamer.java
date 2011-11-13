/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.refactoring.rename.inplace;

import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.lang.LanguageNamesValidation;
import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.impl.FinishMarkAction;
import com.intellij.openapi.command.impl.StartMarkAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory;
import com.intellij.usageView.UsageViewUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;

/**
 * User: anna
 * Date: 11/9/11
 */
public class MemberInplaceRenamer extends VariableInplaceRenamer {
  protected final String myOldName;

  public MemberInplaceRenamer(@NotNull PsiNameIdentifierOwner elementToRename, Editor editor) {
    super(elementToRename, editor);
    myOldName = elementToRename.getName();
    showDialogAdvertisement("RenameElement");
  }

  @Override
  protected PsiElement checkLocalScope() {
    PsiElement scope = super.checkLocalScope();
    if (scope == null) {
      final PsiNamedElement namedElement = getVariable();
      if (namedElement != null) {
        return namedElement.getContainingFile();
      }
    }
    return scope;
  }

  @Override
  protected SearchScope getReferencesSearchScope(VirtualFile file) {
    PsiNamedElement variable = getVariable();
    return variable != null ? new LocalSearchScope(variable.getContainingFile())
                            : ProjectScope.getProjectScope(myProject);
  }

  @Override
  protected boolean appendAdditionalElement(List<Pair<PsiElement, TextRange>> stringUsages) {
    boolean showChooser = super.appendAdditionalElement(stringUsages);
    PsiNamedElement variable = getVariable();
    if (variable != null) {
      RenamePsiElementProcessor processor = RenamePsiElementProcessor.forElement(variable);
      final PsiElement substituted = processor.substituteElementToRename(variable, myEditor);
      if (substituted != null) {
        appendAdditionalElement(stringUsages, variable, substituted);
        processor = RenamePsiElementProcessor.forElement(substituted);
        final HashMap<PsiElement, String> allRenames = new HashMap<PsiElement, String>();

        processor.prepareRenaming(substituted, "", allRenames, new LocalSearchScope(variable.getContainingFile()));

        for (PsiElement element : allRenames.keySet()) {
          appendAdditionalElement(stringUsages, variable, element);
        }
      }
    }
    return showChooser;
  }

  private static void appendAdditionalElement(List<Pair<PsiElement, TextRange>> stringUsages,
                                              PsiNamedElement variable,
                                              PsiElement element) {
    if (element != variable && element instanceof PsiNameIdentifierOwner) {
      final PsiElement identifier = ((PsiNameIdentifierOwner)element).getNameIdentifier();
      if (identifier != null) {
        stringUsages.add(Pair.create(identifier, new TextRange(0, identifier.getTextLength())));
      }
    }
  }

  protected void performRefactoringRename(final String newName,
                                          PsiElement context,
                                          final StartMarkAction markAction) {
    try {
      final PsiNamedElement variable = getVariable();
      if (variable != null && !newName.equals(myOldName)) {
        final NamesValidator namesValidator = LanguageNamesValidation.INSTANCE.forLanguage(variable.getLanguage());
        LOG.assertTrue(namesValidator != null);
        if (namesValidator.isIdentifier(newName, myProject)) {
          RenamePsiElementProcessor processor = RenamePsiElementProcessor.forElement(variable);
          final PsiElement substitutedElement = processor.substituteElementToRename(variable, myEditor);
          if (substitutedElement == null) {
            return;
          }
  
          final String commandName = RefactoringBundle
            .message("renaming.0.1.to.2", UsageViewUtil.getType(variable), UsageViewUtil.getDescriptiveName(variable), newName);
          restore(variable, commandName);
          CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
            public void run() {
              final RenamePsiElementProcessor elementProcessor = RenamePsiElementProcessor.forElement(substitutedElement);
              final RenameProcessor
                renameProcessor = new RenameProcessor(myProject, substitutedElement, newName,
                                                      elementProcessor.isToSearchInComments(substitutedElement),
                                                      elementProcessor.isToSearchForTextOccurrences(substitutedElement));
              for (AutomaticRenamerFactory factory : Extensions.getExtensions(AutomaticRenamerFactory.EP_NAME)) {
                if (factory.isApplicable(substitutedElement) && factory.getOptionName() != null) {
                  if (factory.isEnabled()) {
                    renameProcessor.addRenamerFactory(factory);
                  }
                }
              }
              renameProcessor.run();
              PsiDocumentManager.getInstance(myProject).commitAllDocuments();
            }
          }, commandName, null);
        }
      }
    }
    finally {
      CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
        public void run() {
          FinishMarkAction.finish(myProject, myEditor, markAction);
        }
      }, RENAME_TITLE, null);
    }
  }

  @Override
  protected void collectAdditionalElementsToRename(boolean processTextOccurrences, List<Pair<PsiElement, TextRange>> stringUsages) {
    //do not highlight non-code usages in file
  }

  private void restore(final PsiNamedElement variable, String commandName) {
    final RenamePsiElementProcessor processor = RenamePsiElementProcessor.forElement(variable);
    final PsiElement substituted = processor.substituteElementToRename(variable, myEditor);
    if (substituted != null) {
      final RenamePsiElementProcessor substProcessor = RenamePsiElementProcessor.forElement(substituted);
      final LocalSearchScope localSearchScope = new LocalSearchScope(variable.getContainingFile());
      final Collection<PsiReference> references = ReferencesSearch.search(substituted, localSearchScope).findAll();
      CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {

              final HashMap<PsiElement, String> allRenames = new HashMap<PsiElement, String>();


              substProcessor.prepareRenaming(substituted, "", allRenames, localSearchScope);

              if (substituted != variable && substituted instanceof PsiNameIdentifierOwner) {
                ((PsiNameIdentifierOwner)substituted).setName(myOldName);
              }

              for (PsiElement element : allRenames.keySet()) {
                if (element instanceof PsiNameIdentifierOwner && element != variable) {
                  ((PsiNameIdentifierOwner)element).setName(myOldName);
                }
              }
              for (PsiReference reference : references) {
                reference.handleElementRename(myOldName);
              }


              variable.setName(myOldName);
            }
          });
        }
      }, commandName, null);
                }
  }

  @Override
  protected void moveOffsetAfter(boolean success) {
    super.moveOffsetAfter(success);
    if (!success) {
      if (myElementToRename != null && myElementToRename.isValid()) {
        restore(myElementToRename, RENAME_TITLE);
      }
    }
  }

  @Override
  protected void restoreStateBeforeDialogWouldBeShown() {
    PsiNamedElement variable = getVariable();
    final TemplateState state = TemplateManagerImpl.getTemplateState(myEditor);
    assert state != null;
    final String commandName = RefactoringBundle
            .message("renaming.0.1.to.2", UsageViewUtil.getType(variable), UsageViewUtil.getDescriptiveName(variable), variable.getName());
    Runnable runnable = new Runnable() {
      public void run() {
        state.gotoEnd(true);
      }
    };
    CommandProcessor.getInstance().executeCommand(myProject, runnable, commandName, null);
  }

  protected StartMarkAction startRename() throws StartMarkAction.AlreadyStartedException {
    final StartMarkAction[] markAction = new StartMarkAction[1];
    final StartMarkAction.AlreadyStartedException[] ex = new StartMarkAction.AlreadyStartedException[1];
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      @Override
      public void run() {
        try {
          markAction[0] = StartMarkAction.start(myEditor, myProject, getVariable(), RENAME_TITLE);
        }
        catch (StartMarkAction.AlreadyStartedException e) {
          ex[0] = e;
        }
      }
    }, RENAME_TITLE, null);
    if (ex[0] != null) throw ex[0];
    return markAction[0];
  }
}
