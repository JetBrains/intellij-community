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

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupEx;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import slowCheck.Generator;
import slowCheck.IntDistribution;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author peter
 */
class InvokeCompletion extends ActionOnRange implements MadTestingAction {
  final int itemIndexRaw;
  LookupElement selectedItem;
  final char completionChar;
  private final PsiFile myFile;

  InvokeCompletion(Document document, int offset, int itemIndexRaw, char completionChar, PsiFile file) {
    super(document, offset, offset);
    this.itemIndexRaw = itemIndexRaw;
    this.completionChar = completionChar;
    myFile = file;
  }

  @Override
  public String toString() {
    return "CompletionInvocation{" +
           "offset=" + getStartOffset() +
           ", selectedItem=" + selectedItem + "(" + itemIndexRaw + ")" +
           ", completionChar=" + StringUtil.escapeStringCharacters(String.valueOf(completionChar)) +
           '}';
  }

  @Override
  public void performAction() {
    Project project = myFile.getProject();
    Editor editor =
      FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, myFile.getVirtualFile(), 0), true);

    PsiDocumentManager.getInstance(project).commitAllDocuments();
    int offset = getStartOffset();
    if (offset < 0) return;

    editor.getCaretModel().moveToOffset(offset);
    
    AbstractApplyAndRevertTestCase.restrictChangesToDocument(editor.getDocument(), () -> {
      Disposable raiseCompletionLimit = Disposer.newDisposable();
      Registry.get("ide.completion.variant.limit").setValue(100_000, raiseCompletionLimit);
      try {
        performCompletion(editor);
        PsiTestUtil.checkPsiStructureWithCommit(myFile, PsiTestUtil::checkFileStructure);
      }
      finally {
        Disposer.dispose(raiseCompletionLimit);
        LookupManager.getInstance(project).hideActiveLookup();
        UIUtil.dispatchAllInvocationEvents();
      }
    });
  }

  private void performCompletion(Editor editor) {
    String expectedVariant = getExpectedVariant(editor, myFile);

    new CodeCompletionHandlerBase(CompletionType.BASIC).invokeCompletion(myFile.getProject(), editor);

    LookupEx lookup = LookupManager.getActiveLookup(editor);
    if (lookup == null) {
      if (expectedVariant == null) return;
      TestCase.fail("No lookup, but expected " + expectedVariant + " among completion variants");
    }

    List<LookupElement> items = lookup.getItems();
    if (expectedVariant != null) {
      LookupElement sameItem = ContainerUtil.find(items, e -> e.getAllLookupStrings().contains(expectedVariant));
      TestCase.assertNotNull("No variant " + expectedVariant + " among " + items, sameItem);
    }

    checkNoDuplicates(items);

    LookupElement item = items.get(itemIndexRaw % items.size());
    selectedItem = item;
    ((LookupImpl)lookup).finishLookup(completionChar, item);
  }

  private static void checkNoDuplicates(List<LookupElement> items) {
    Set<List<?>> presentations = new HashSet<>();
    for (LookupElement item : items) {
      LookupElementPresentation p = LookupElementPresentation.renderElement(item);
      List<Object> info = Arrays.asList(p.getItemText(), p.getItemTextForeground(), p.isItemTextBold(), p.isItemTextUnderlined(),
                                        p.getTailFragments(),
                                        p.getTypeText(), p.getTypeIcon(), p.isTypeGrayed(),
                                        p.isStrikeout());
      if (!presentations.add(info)) {
        TestCase.fail("Duplicate suggestions: " + p);
      }
    }
  }

  @Nullable
  private static String getExpectedVariant(Editor editor, PsiFile file) {
    PsiElement leaf = file.findElementAt(editor.getCaretModel().getOffset());
    PsiReference ref = file.findReferenceAt(editor.getCaretModel().getOffset());
    PsiElement refTarget = ref == null ? null : ref.resolve();
    if (leaf == null) {
      return null;
    }
    String leafText = leaf.getText();
    if (leafText.isEmpty() ||
        !Character.isLetter(leafText.charAt(0)) ||
        leaf instanceof PsiWhiteSpace ||
        PsiTreeUtil.getParentOfType(leaf, PsiComment.class, false) != null) {
      return null;
    }
    if (ref != null) {
      if (refTarget == null) return null;
      if (ref instanceof PsiJavaCodeReferenceElement && !shouldSuggestJavaTarget((PsiJavaCodeReferenceElement)ref)) {
        return null;
      }
    }
    else {
      if (!SyntaxTraverser.psiTraverser(file).filter(PsiErrorElement.class).isEmpty()) {
        return null;
      }
      if (leaf instanceof PsiIdentifier) return null; // it's not a ref, just some name
    }
    return leafText;
  }

  private static boolean shouldSuggestJavaTarget(PsiJavaCodeReferenceElement ref) {
    if (PsiTreeUtil.getParentOfType(ref, PsiPackageStatement.class) == null) return false;

    PsiElement target = ref.resolve();
    if (!ref.isQualified() && target instanceof PsiPackage) return false;
    return target != null;
  }

  @NotNull
  static Generator<InvokeCompletion> completions(PsiFile psiFile) {
    Document document = psiFile.getViewProvider().getDocument();
    return Generator.from(data -> {
      int offset = data.drawInt(IntDistribution.uniform(0, document.getTextLength()));
      int itemIndex = data.drawInt(IntDistribution.uniform(0, 100));
      char c = Generator.sampledFrom('\n', '\t', '\r', ' ', '.', '(').generateUnstructured(data);
      return new InvokeCompletion(document, offset, itemIndex, c, psiFile);
    });
  }
}
