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
package com.intellij.lang;

import com.intellij.codeInsight.editorActions.smartEnter.SmartEnterProcessor;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.OrderedSet;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ignatov
 */
public abstract class SmartEnterProcessorWithFixers extends SmartEnterProcessor {
  protected static final Logger LOG = Logger.getInstance(SmartEnterProcessorWithFixers.class);
  protected static final int MAX_ATTEMPTS = 20;
  protected static final Key<Long> SMART_ENTER_TIMESTAMP = Key.create("smartEnterOriginalTimestamp");

  protected int myFirstErrorOffset = Integer.MAX_VALUE;

  private final List<Fixer<? extends SmartEnterProcessorWithFixers>> myFixers = new ArrayList<Fixer<? extends SmartEnterProcessorWithFixers>>();
  private final List<FixEnterProcessor> myEnterProcessors = new ArrayList<FixEnterProcessor>();

  protected static void plainEnter(@NotNull final Editor editor) {
    getEnterHandler().execute(editor, ((EditorEx)editor).getDataContext());
  }

  protected static EditorActionHandler getEnterHandler() {
    return EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_START_NEW_LINE);
  }

  protected static boolean isModified(@NotNull final Editor editor) {
    final Long timestamp = editor.getUserData(SMART_ENTER_TIMESTAMP);
    assert timestamp != null;
    return editor.getDocument().getModificationStamp() != timestamp.longValue();
  }

  public boolean doNotStepInto(PsiElement element) {
    return false;
  }

  @Override
  public boolean process(@NotNull final Project project, @NotNull final Editor editor, @NotNull final PsiFile psiFile) {
    final Document document = editor.getDocument();
    final String textForRollback = document.getText();
    try {
      editor.putUserData(SMART_ENTER_TIMESTAMP, editor.getDocument().getModificationStamp());
      myFirstErrorOffset = Integer.MAX_VALUE;
      process(project, editor, psiFile, 0);
    }
    catch (TooManyAttemptsException e) {
      document.replaceString(0, document.getTextLength(), textForRollback);
    }
    finally {
      editor.putUserData(SMART_ENTER_TIMESTAMP, null);
    }
    return true;
  }

  protected void process(@NotNull final Project project, @NotNull final Editor editor, @NotNull final PsiFile file, final int attempt)
    throws TooManyAttemptsException {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("codeassists.complete.statement");
    if (attempt > MAX_ATTEMPTS) throw new TooManyAttemptsException();

    try {
      commit(editor);
      if (myFirstErrorOffset != Integer.MAX_VALUE) {
        editor.getCaretModel().moveToOffset(myFirstErrorOffset);
      }

      myFirstErrorOffset = Integer.MAX_VALUE;

      PsiElement atCaret = getStatementAtCaret(editor, file);
      if (atCaret == null) {
        return;
      }

      OrderedSet<PsiElement> queue = new OrderedSet<PsiElement>();
      collectAllElements(atCaret, queue, true);
      queue.add(atCaret);

      for (PsiElement psiElement : queue) {
        for (Fixer fixer : myFixers) {
          fixer.apply(editor, this, psiElement);
          if (LookupManager.getInstance(project).getActiveLookup() != null) {
            return;
          }
          if (isUncommited(project) || !psiElement.isValid()) {
            moveCaretInsideBracesIfAny(editor, file);
            process(project, editor, file, attempt + 1);
            return;
          }
        }
      }

      doEnter(atCaret, file, editor);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  protected void collectAllElements(@NotNull PsiElement element, @NotNull OrderedSet<PsiElement> result, boolean recursive) {
    result.add(0, element);
    if (doNotStepInto(element)) {
      if (!recursive) return;
      recursive = false;
    }

    collectAdditionalElements(element, result);

    for (PsiElement child : element.getChildren()) {
      collectAllElements(child, result, recursive);
    }
  }

  protected void doEnter(@NotNull PsiElement atCaret, @NotNull PsiFile file, @NotNull Editor editor) throws IncorrectOperationException {
    if (myFirstErrorOffset != Integer.MAX_VALUE) {
      editor.getCaretModel().moveToOffset(myFirstErrorOffset);
      reformat(atCaret);
      return;
    }

    reformat(atCaret);
    commit(editor);

    for (FixEnterProcessor enterProcessor : myEnterProcessors) {
      if (enterProcessor.doEnter(atCaret, file, editor, isModified(editor))) {
        return;
      }
    }

    if (!isModified(editor)) {
      plainEnter(editor);
    }
    else {
      if (myFirstErrorOffset == Integer.MAX_VALUE) {
        editor.getCaretModel().moveToOffset(atCaret.getTextRange().getEndOffset());
      }
      else {
        editor.getCaretModel().moveToOffset(myFirstErrorOffset);
      }
    }
  }

  protected void addEnterProcessors(FixEnterProcessor... processors) {
    ContainerUtil.addAllNotNull(myEnterProcessors, processors);
  }

  protected void addFixers(Fixer<? extends SmartEnterProcessorWithFixers>... fixers) {
    ContainerUtil.addAllNotNull(myFixers, fixers);
  }

  protected void collectAdditionalElements(@NotNull PsiElement element, @NotNull List<PsiElement> result) {
  }

  protected void moveCaretInsideBracesIfAny(@NotNull Editor editor, @NotNull PsiFile file) throws IncorrectOperationException {
  }

  public static class TooManyAttemptsException extends Exception {
  }

  public abstract static class Fixer<P extends SmartEnterProcessorWithFixers> {
    abstract public void apply(@NotNull Editor editor, @NotNull P processor, @NotNull PsiElement element) throws IncorrectOperationException;
  }

  public abstract static class FixEnterProcessor {
    abstract public boolean doEnter(PsiElement atCaret, PsiFile file, @NotNull Editor editor, boolean modified);
    
    protected void plainEnter(@NotNull Editor editor) {
      SmartEnterProcessorWithFixers.plainEnter(editor);
    }
  }

  @Override
  public void commit(@NotNull Editor editor) { // pull up
    super.commit(editor);
  }
}
