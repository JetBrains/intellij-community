/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.propertyBased;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import slowCheck.DataStructure;
import slowCheck.Generator;

import java.util.List;

class InvokeIntention extends ActionOnRange {
  private final PsiFile myFile;
  private final int myIntentionIndex;
  private IntentionAction myIntentionAction;

  private InvokeIntention(PsiFile file, int offset, int intentionIndex) {
    super(file.getViewProvider().getDocument(), offset, offset);
    myFile = file;
    myIntentionIndex = intentionIndex;
  }

  @NotNull
  static InvokeIntention generate(@NotNull PsiFile psiFile, @NotNull DataStructure data) {
    return new InvokeIntention(psiFile,
                               Generator.integers(0, psiFile.getTextLength()).generateValue(data),
                               Generator.integers(0, 100).generateValue(data));
  }

  @Override
  public String toString() {
    String name = myIntentionAction == null ? "index " + String.valueOf(myIntentionIndex) : myIntentionAction.getText();
    if (name == null) {
      name = myIntentionAction.toString();
    }
    return "Intention: " + myFile.getVirtualFile().getPath() + ", offset " + getStartOffset() + ", invoke " + name;
  }

  void invokeIntention() {
    int offset = getStartOffset();
    if (offset < 0) return;

    Editor editor = FileEditorManager.getInstance(myFile.getProject()).openTextEditor(new OpenFileDescriptor(myFile.getProject(), myFile.getVirtualFile(), offset), true);

    CodeInsightTestFixtureImpl.instantiateAndRun(myFile, editor, new int[0], false);

    IntentionAction intention = myIntentionAction = getRandomIntention(editor);
    if (intention == null) return;

    System.out.println("apply " + this);
    String currentFileText = myFile.getText();

    Document changedDocument = getDocumentToBeChanged(intention);

    Runnable r = () -> CodeInsightTestFixtureImpl.invokeIntention(intention, myFile, editor, intention.getText());
    if (changedDocument != null) {
      AbstractApplyAndRevertTestCase.restrictChangesToDocument(changedDocument, r);
    } else {
      r.run();
    }

    if (changedDocument != null && 
        PsiDocumentManager.getInstance(myFile.getProject()).isDocumentBlockedByPsi(changedDocument)) {
      throw new AssertionError("Document is left blocked by PSI");
    }
    if (myFile.textMatches(currentFileText)) {
      throw new AssertionError("No change was performed: " + currentFileText);
    }

    PsiTestUtil.checkStubsMatchText(myFile);
  }

  @Nullable
  private Document getDocumentToBeChanged(IntentionAction intention) {
    PsiElement changedElement = intention.getElementToMakeWritable(myFile);
    PsiFile changedFile = changedElement == null ? null : changedElement.getContainingFile();
    return changedFile == null ? null : changedFile.getViewProvider().getDocument();
  }

  @Nullable
  private IntentionAction getRandomIntention(Editor editor) {
    List<IntentionAction> actions = ContainerUtil.filter(CodeInsightTestFixtureImpl.getAvailableIntentions(editor, myFile),
                                                         action -> action.startInWriteAction() &&
                                                                   !shouldSkipIntention(action.getText()));
    return actions.isEmpty() ? null : actions.get(myIntentionIndex % actions.size());
  }

  private static boolean shouldSkipIntention(String actionText) {
    return actionText.startsWith("Flip") ||
           actionText.startsWith("Attach annotations") ||
           actionText.startsWith("Convert to string literal") ||
           actionText.startsWith("Optimize imports") || // https://youtrack.jetbrains.com/issue/IDEA-173801
           actionText.startsWith("Move assignment to field declaration") || // https://youtrack.jetbrains.com/issue/IDEA-174956
           actionText.startsWith("Add on demand static import") || // https://youtrack.jetbrains.com/issue/IDEA-174965
           actionText.startsWith("Make method default") ||
           actionText.contains("to custom tags") || // changes only inspection settings
           actionText.startsWith("Typo: Change to...") || // doesn't change file text (starts live template)
           actionText.startsWith("Change class type parameter") || // doesn't change file text (starts live template)
           actionText.startsWith("Rename reference") || // doesn't change file text (starts live template)
           actionText.startsWith("Detail exceptions") || // can produce uncompilable code if 'catch' section contains 'instanceof's
           actionText.startsWith("Insert call to super method") || // super method can declare checked exceptions, unexpected at this point
           actionText.startsWith("Unimplement");
  }

}
