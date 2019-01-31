// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl.fixes;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.codeInsight.template.impl.EmptyNode;
import com.intellij.codeInsight.template.impl.MacroCallNode;
import com.intellij.codeInsight.template.macro.CompleteMacro;
import com.intellij.codeInspection.*;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.DocumentUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.extension.JsonLikeSyntaxAdapter;
import com.jetbrains.jsonSchema.impl.JsonValidationError;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class AddMissingPropertyFix implements LocalQuickFix, BatchQuickFix<CommonProblemDescriptor> {
  private final JsonValidationError.MissingMultiplePropsIssueData myData;
  private final JsonLikeSyntaxAdapter myQuickFixAdapter;

  public AddMissingPropertyFix(JsonValidationError.MissingMultiplePropsIssueData data,
                               JsonLikeSyntaxAdapter quickFixAdapter) {
    myData = data;
    myQuickFixAdapter = quickFixAdapter;
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return "Add missing properties";
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getName() {
    return "Add missing " + myData.getMessage(true);
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    Ref<Boolean> hadComma = Ref.create(false);
    VirtualFile file = element.getContainingFile().getVirtualFile();
    PsiElement newElement = performFix(element, hadComma);
    // if we have more than one property, don't expand templates and don't move the caret
    if (newElement == null) return;

    PsiElement value = myQuickFixAdapter.getPropertyValue(newElement);
    FileEditor fileEditor = FileEditorManager.getInstance(project).getSelectedEditor(file);
    EditorEx editor = EditorUtil.getEditorEx(fileEditor);
    assert editor != null;
    if (value == null) {
      WriteAction.run(() -> editor.getCaretModel().moveToOffset(newElement.getTextRange().getEndOffset()));
      return;
    }
    TemplateManager templateManager = TemplateManager.getInstance(project);
    TemplateBuilderImpl builder = new TemplateBuilderImpl(newElement);
    String text = value.getText();
    boolean isEmptyArray = StringUtil.equalsIgnoreWhitespaces(text, "[]");
    boolean isEmptyObject = StringUtil.equalsIgnoreWhitespaces(text, "{}");
    boolean goInside = isEmptyArray || isEmptyObject || StringUtil.isQuotedString(text);
    TextRange range = goInside ? TextRange.create(1, text.length() - 1) : TextRange.create(0, text.length());
    builder.replaceElement(value, range, myData.myMissingPropertyIssues.iterator().next().enumItemsCount > 1 || isEmptyObject
                                          ? new MacroCallNode(new CompleteMacro())
                                          : isEmptyArray ? new EmptyNode() : new ConstantNode(goInside ? StringUtil.unquoteString(text) : text));
    editor.getCaretModel().moveToOffset(newElement.getTextRange().getStartOffset());
    if (PsiTreeUtil.nextLeaf(newElement) != null) {
      builder.setEndVariableAfter(newElement);
    }
    else {
      builder.setEndVariableBefore(newElement.getLastChild());
    }
    WriteAction.run(() -> {
            Template template = builder.buildInlineTemplate();
            template.setToReformat(true);
            templateManager.startTemplate(editor, template);
          });
  }

  @Nullable
  public PsiElement performFix(@Nullable PsiElement node, @NotNull Ref<Boolean> hadComma) {
    if (node == null) return null;
    PsiElement element = node instanceof PsiFile ? node.getFirstChild() : node;
    Ref<PsiElement> newElementRef = Ref.create(null);

    WriteAction.run(() -> {
      boolean isSingle = myData.myMissingPropertyIssues.size() == 1;
      PsiElement processedElement = element;
      List<JsonValidationError.MissingPropertyIssueData> reverseOrder
        = ContainerUtil.reverse(ContainerUtil.newArrayList(myData.myMissingPropertyIssues));
      for (JsonValidationError.MissingPropertyIssueData issue: reverseOrder) {
        Object defaultValueObject = issue.defaultValue;
        String defaultValue = defaultValueObject instanceof String ? StringUtil.wrapWithDoubleQuote(defaultValueObject.toString()) : null;
        PsiElement property = myQuickFixAdapter.createProperty(issue.propertyName, defaultValue == null
                                                                                   ? myQuickFixAdapter
                                                                                     .getDefaultValueFromType(issue.propertyType)
                                                                                   : defaultValue, element);
        PsiElement newElement;
        if (processedElement instanceof LeafPsiElement) {
          newElement = myQuickFixAdapter.adjustPropertyAnchor((LeafPsiElement)processedElement).addBefore(property, null);
        }
        else {
          if (processedElement == element) {
            newElement = processedElement.addBefore(property, processedElement.getLastChild());
          }
          else {
            newElement = processedElement.getParent().addBefore(property, processedElement);
          }
        }
        PsiElement adjusted = myQuickFixAdapter.adjustNewProperty(newElement);
        hadComma.set(myQuickFixAdapter.ensureComma(adjusted, PsiTreeUtil.skipWhitespacesForward(newElement)));
        if (!hadComma.get()) {
          hadComma.set(processedElement == element && myQuickFixAdapter.ensureComma(PsiTreeUtil.skipWhitespacesBackward(newElement), adjusted));
        }
        processedElement = adjusted;
        if (isSingle) {
          newElementRef.set(adjusted);
        }
      }
     });

    return newElementRef.get();
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void applyFix(@NotNull Project project,
                       @NotNull CommonProblemDescriptor[] descriptors,
                       @NotNull List<PsiElement> psiElementsToIgnore,
                       @Nullable Runnable refreshViews) {
    List<Pair<AddMissingPropertyFix, PsiElement>> propFixes = ContainerUtil.newArrayList();
    for (CommonProblemDescriptor descriptor: descriptors) {
      if (!(descriptor instanceof ProblemDescriptor)) continue;
      QuickFix[] fixes = descriptor.getFixes();
      if (fixes == null) continue;
      AddMissingPropertyFix fix = getWorkingQuickFix(fixes);
      if (fix == null) continue;
      propFixes.add(Pair.create(fix, ((ProblemDescriptor)descriptor).getPsiElement()));
    }

    DocumentUtil.writeInRunUndoTransparentAction(() -> propFixes.forEach(fix ->
                                                   fix.first.performFix(fix.second, Ref.create(false))));
  }

  @Nullable
  private static AddMissingPropertyFix getWorkingQuickFix(@NotNull QuickFix[] fixes) {
    for (QuickFix fix : fixes) {
      if (fix instanceof AddMissingPropertyFix) {
        return (AddMissingPropertyFix)fix;
      }
    }
    return null;
  }
}
