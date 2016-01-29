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

package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.completion.CompletionPhase;
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public abstract class BaseCompleteMacro extends Macro {
  private final String myName;

  protected BaseCompleteMacro(@NonNls String name) {
    myName = name;
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public String getPresentableName() {
    return myName + "()";
  }

  @Override
  @NotNull
  public String getDefaultValue() {
    return "a";
  }

  @Override
  public final Result calculateResult(@NotNull Expression[] params, final ExpressionContext context) {
    return new InvokeActionResult(
      new Runnable() {
        @Override
        public void run() {
          invokeCompletion(context);
        }
      }
    );
  }

  private void invokeCompletion(final ExpressionContext context) {
    final Project project = context.getProject();
    final Editor editor = context.getEditor();

    final PsiFile psiFile = editor != null ? PsiUtilBase.getPsiFileInEditor(editor, project) : null;
    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        if (project.isDisposed() || editor == null || editor.isDisposed() || psiFile == null || !psiFile.isValid()) return;

        // it's invokeLater, so another completion could have started
        if (CompletionServiceImpl.getCompletionService().getCurrentCompletion() != null) return;

        CommandProcessor.getInstance().executeCommand(project, new Runnable() {
          @Override
          public void run() {
            // if we're in some completion's insert handler, make sure our new completion isn't treated as the second invocation
            CompletionServiceImpl.setCompletionPhase(CompletionPhase.NoCompletion);
            
            invokeCompletionHandler(project, editor);
            Lookup lookup = LookupManager.getInstance(project).getActiveLookup();

            if (lookup != null) {
              lookup.addLookupListener(new MyLookupListener(context));
            }
          }
        }, "", null);
      }
    };
    ApplicationManager.getApplication().invokeLater(runnable);
  }

  private static void considerNextTab(Editor editor) {
    TemplateState templateState = TemplateManagerImpl.getTemplateState(editor);
    if (templateState != null) {
      TextRange range = templateState.getCurrentVariableRange();
      if (range != null && range.getLength() > 0) {
        int caret = editor.getCaretModel().getOffset();
        if (caret == range.getEndOffset()) {
          templateState.nextTab();
        }
        else if (caret > range.getEndOffset()) {
          templateState.gotoEnd(true);
        }
      }
    }
  }

  protected abstract void invokeCompletionHandler(Project project, Editor editor);

  private static class MyLookupListener extends LookupAdapter {
    private final ExpressionContext myContext;

    public MyLookupListener(@NotNull ExpressionContext context) {
      myContext = context;
    }

    @Override
    public void itemSelected(LookupEvent event) {
      LookupElement item = event.getItem();
      if (item == null) return;

      char c = event.getCompletionChar();
      if (!LookupEvent.isSpecialCompletionChar(c)) {
        return;
      }

      for(TemplateCompletionProcessor processor: Extensions.getExtensions(TemplateCompletionProcessor.EP_NAME)) {
        if (!processor.nextTabOnItemSelected(myContext, item)) {
          return;
        }
      }

      final Project project = myContext.getProject();
      if (project == null) {
        return;
      }
      
      Runnable runnable = new Runnable() {
        @Override
        public void run() {
          new WriteCommandAction(project) {
            @Override
            protected void run(@NotNull com.intellij.openapi.application.Result result) throws Throwable {
              Editor editor = myContext.getEditor();
              if (editor != null) {
                considerNextTab(editor);
              }
            }
          }.execute();
        }
      };
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        runnable.run();
      } else {
        ApplicationManager.getApplication().invokeLater(runnable, ModalityState.current(), project.getDisposed());
      }

    }
  }
}
