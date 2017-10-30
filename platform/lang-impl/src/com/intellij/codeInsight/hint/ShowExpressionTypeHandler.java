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

package com.intellij.codeInsight.hint;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.lang.ExpressionTypeProvider;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageExpressionTypes;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pass;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.IntroduceTargetChooser;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;

public class ShowExpressionTypeHandler implements CodeInsightActionHandler {
  private boolean myRequestFocus;

  public ShowExpressionTypeHandler(boolean requestFocus) {
    myRequestFocus = requestFocus;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  public void invoke(@NotNull final Project project, @NotNull final Editor editor, @NotNull PsiFile file) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    Language language = PsiUtilCore.getLanguageAtOffset(file, editor.getCaretModel().getOffset());
    final Set<ExpressionTypeProvider> handlers = getHandlers(project, language, file.getViewProvider().getBaseLanguage());
    if (handlers.isEmpty()) return;

    boolean exactRange = false;
    TextRange range = EditorUtil.getSelectionInAnyMode(editor);
    final Map<PsiElement, ExpressionTypeProvider> map = ContainerUtil.newLinkedHashMap();
    int offset = TargetElementUtil.adjustOffset(file, editor.getDocument(), editor.getCaretModel().getOffset());
    for (int i = 0; i < 3 && map.isEmpty() && offset > i; i++) {
      PsiElement elementAt = file.findElementAt(offset - i);
      if (elementAt == null) continue;
      for (ExpressionTypeProvider handler : handlers) {
        for (PsiElement element : ((ExpressionTypeProvider<? extends PsiElement>)handler).getExpressionsAt(elementAt)) {
          TextRange r = element.getTextRange();
          if (exactRange && !r.equals(range) || !r.contains(range)) continue;
          if (!exactRange) exactRange = r.equals(range);
          map.put(element, handler);
        }
      }
    }
    Pass<PsiElement> callback = new Pass<PsiElement>() {
      @Override
      public void pass(@NotNull PsiElement expression) {
        //noinspection unchecked
        ExpressionTypeProvider<PsiElement> provider = ObjectUtils.assertNotNull(map.get(expression));
        final String informationHint = provider.getInformationHint(expression);
        TextRange range = expression.getTextRange();
        editor.getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
        ApplicationManager.getApplication().invokeLater(() -> {
          HintManager.getInstance().setRequestFocusForNextHint(myRequestFocus);
          HintManager.getInstance().showInformationHint(editor, informationHint);
        });
      }
    };
    if (map.isEmpty()) {
      ApplicationManager.getApplication().invokeLater(() -> {
        String errorHint = ObjectUtils.assertNotNull(ContainerUtil.getFirstItem(handlers)).getErrorHint();
        HintManager.getInstance().showErrorHint(editor, errorHint);
      });
    }
    else if (map.size() == 1) {
      callback.pass(ObjectUtils.assertNotNull(ContainerUtil.getFirstItem(map.keySet())));
    }
    else {
      IntroduceTargetChooser.showChooser(
        editor, ContainerUtil.newArrayList(map.keySet()), callback,
        expression -> expression.getText()
      );
    }
  }

  @NotNull
  public static Set<ExpressionTypeProvider> getHandlers(final Project project, Language... languages) {
    return JBIterable.of(languages).flatten(
      language -> DumbService.getInstance(project).filterByDumbAwareness(LanguageExpressionTypes.INSTANCE.allForLanguage(language))).addAllTo(ContainerUtil.newLinkedHashSet());
  }

}

