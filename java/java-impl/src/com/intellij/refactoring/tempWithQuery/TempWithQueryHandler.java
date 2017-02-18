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
package com.intellij.refactoring.tempWithQuery;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.extractMethod.ExtractMethodHandler;
import com.intellij.refactoring.extractMethod.ExtractMethodProcessor;
import com.intellij.refactoring.extractMethod.PrepareFailedException;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.duplicates.DuplicatesImpl;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class TempWithQueryHandler implements RefactoringActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.tempWithQuery.TempWithQueryHandler");

  private static final String REFACTORING_NAME = RefactoringBundle.message("replace.temp.with.query.title");

  public void invoke(@NotNull final Project project, final Editor editor, PsiFile file, DataContext dataContext) {
    PsiElement element = TargetElementUtil.findTargetElement(editor, TargetElementUtil
                                                                       .ELEMENT_NAME_ACCEPTED |
                                                                     TargetElementUtil
                                                                       .LOOKUP_ITEM_ACCEPTED |
                                                                     TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    if (!(element instanceof PsiLocalVariable)) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("error.wrong.caret.position.local.name"));
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.REPLACE_TEMP_WITH_QUERY);
      return;
    }

    invokeOnVariable(file, project, (PsiLocalVariable)element, editor);
  }

  private static void invokeOnVariable(final PsiFile file, final Project project, final PsiLocalVariable local, final Editor editor) {
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, file)) return;

    String localName = local.getName();
    final PsiExpression initializer = local.getInitializer();
    if (initializer == null) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("variable.has.no.initializer", localName));
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.REPLACE_TEMP_WITH_QUERY);
      return;
    }

    final PsiReference[] refs = ReferencesSearch.search(local, GlobalSearchScope.projectScope(project), false).toArray(
      PsiReference.EMPTY_ARRAY);

    if (refs.length == 0) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("variable.is.never.used", localName));
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.REPLACE_TEMP_WITH_QUERY);
      return;
    }

    final HighlightManager highlightManager = HighlightManager.getInstance(project);
    ArrayList<PsiReference> array = new ArrayList<>();
    EditorColorsManager manager = EditorColorsManager.getInstance();
    final TextAttributes attributes = manager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    for (PsiReference ref : refs) {
      PsiElement refElement = ref.getElement();
      if (PsiUtil.isAccessedForWriting((PsiExpression)refElement)) {
        array.add(ref);
      }
      if (!array.isEmpty()) {
        PsiReference[] refsForWriting = array.toArray(new PsiReference[array.size()]);
        highlightManager.addOccurrenceHighlights(editor, refsForWriting, attributes, true, null);
        String message =  RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("variable.is.accessed.for.writing", localName));
        CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.REPLACE_TEMP_WITH_QUERY);
        WindowManager.getInstance().getStatusBar(project).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
        return;
      }
    }

    final ExtractMethodProcessor processor = new ExtractMethodProcessor(
            project, editor,
            new PsiElement[]{initializer}, local.getType(),
            REFACTORING_NAME, localName, HelpID.REPLACE_TEMP_WITH_QUERY
    );

    try {
      if (!processor.prepare()) return;
    }
    catch (PrepareFailedException e) {
      CommonRefactoringUtil.showErrorHint(project, editor, e.getMessage(), REFACTORING_NAME, HelpID.REPLACE_TEMP_WITH_QUERY);
      ExtractMethodHandler.highlightPrepareError(e, file, editor, project);
      return;
    }
    final PsiClass targetClass = processor.getTargetClass();
    if (targetClass != null && targetClass.isInterface()) {
      String message = RefactoringBundle.message("cannot.replace.temp.with.query.in.interface");
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.REPLACE_TEMP_WITH_QUERY);
      return;
    }


    if (processor.showDialog()) {
      CommandProcessor.getInstance().executeCommand(
        project, () -> {
          final Runnable action = () -> {
            try {
              processor.doRefactoring();

              local.normalizeDeclaration();

              PsiExpression initializer1 = local.getInitializer();

              PsiExpression[] exprs = new PsiExpression[refs.length];
              for (int idx = 0; idx < refs.length; idx++) {
                PsiElement ref = refs[idx].getElement();
                exprs[idx] = (PsiExpression) ref.replace(initializer1);
              }
              PsiDeclarationStatement declaration = (PsiDeclarationStatement) local.getParent();
              declaration.delete();

              highlightManager.addOccurrenceHighlights(editor, exprs, attributes, true, null);
            } catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          };

          PostprocessReformattingAspect.getInstance(project).postponeFormattingInside(() -> {
            ApplicationManager.getApplication().runWriteAction(action);
            DuplicatesImpl.processDuplicates(processor, project, editor);
          });
        },
        REFACTORING_NAME,
        null
      );
    }


    WindowManager.getInstance().getStatusBar(project).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
  }

  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    if (elements.length == 1 && elements[0] instanceof PsiLocalVariable) {
      if (dataContext != null) {
        final PsiFile file = CommonDataKeys.PSI_FILE.getData(dataContext);
        final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
        if (file != null && editor != null) {
          invokeOnVariable(file, project, (PsiLocalVariable)elements[0], editor);
        }
      }
    }
  }
}