// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json.codeinsight;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.json.*;
import com.intellij.json.psi.*;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Compliance checks include
 * <ul>
 * <li>Usage of line and block commentaries</li>
 * <li>Usage of single quoted strings</li>
 * <li>Usage of identifiers (unqouted words)</li>
 * <li>Not double quoted string literal is used as property key</li>
 * <li>Multiple top-level values</li>
 * </ul>
 *
 * @author Mikhail Golubev
 */
public class JsonStandardComplianceInspection extends LocalInspectionTool {

  public boolean myWarnAboutComments = true;
  public boolean myWarnAboutNanInfinity = true;
  public boolean myWarnAboutTrailingCommas = true;
  public boolean myWarnAboutMultipleTopLevelValues = true;

  @NotNull
  @Override
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    if (!JsonDialectUtil.isStandardJson(holder.getFile())) return PsiElementVisitor.EMPTY_VISITOR;
    return new StandardJsonValidatingElementVisitor(holder);
  }

  @Nullable
  private static PsiElement findTrailingComma(@NotNull JsonContainer container, @NotNull IElementType ending) {
    final PsiElement lastChild = container.getLastChild();
    if (lastChild.getNode().getElementType() != ending) {
      return null;
    }
    final PsiElement beforeEnding = PsiTreeUtil.skipWhitespacesAndCommentsBackward(lastChild);
    if (beforeEnding != null && beforeEnding.getNode().getElementType() == JsonElementTypes.COMMA) {
      return beforeEnding;
    }
    return null;
  }


  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);
    optionsPanel.addCheckbox(JsonBundle.message("inspection.compliance.option.comments"), "myWarnAboutComments");
    optionsPanel.addCheckbox(JsonBundle.message("inspection.compliance.option.multiple.top.level.values"), "myWarnAboutMultipleTopLevelValues");
    optionsPanel.addCheckbox(JsonBundle.message("inspection.compliance.option.trailing.comma"), "myWarnAboutTrailingCommas");
    optionsPanel.addCheckbox(JsonBundle.message("inspection.compliance.option.nan.infinity"), "myWarnAboutNanInfinity");
    return optionsPanel;
  }

  private static class AddDoubleQuotesFix implements LocalQuickFix {
    @NotNull
    @Override
    public String getFamilyName() {
      return JsonBundle.message("quickfix.add.double.quotes.desc");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final String rawText = element.getText();
      if (element instanceof JsonLiteral || element instanceof JsonReferenceExpression) {
        String content = JsonPsiUtil.stripQuotes(rawText);
        if (element instanceof JsonStringLiteral && rawText.startsWith("'")) {
          content = escapeSingleQuotedStringContent(content);
        }
        final PsiElement replacement = new JsonElementGenerator(project).createValue("\"" + content + "\"");
        CodeStyleManager.getInstance(project).performActionWithFormatterDisabled((Runnable)() -> element.replace(replacement));
      }
      else {
        Logger.getInstance(JsonStandardComplianceInspection.class)
          .error("Quick fix was applied to unexpected element", rawText, element.getParent().getText());
      }
    }

    @NotNull
    private static String escapeSingleQuotedStringContent(@NotNull String content) {
      final StringBuilder result = new StringBuilder();
      boolean nextCharEscaped = false;
      for (int i = 0; i < content.length(); i++) {
        final char c = content.charAt(i);
        if ((nextCharEscaped && c != '\'') || (!nextCharEscaped && c == '"')) {
          result.append('\\');
        }
        if (c != '\\' || nextCharEscaped) {
          result.append(c);
          nextCharEscaped = false;
        }
        else {
          nextCharEscaped = true;
        }
      }
      if (nextCharEscaped) {
        result.append('\\');
      }
      return result.toString();
    }
  }

  protected class StandardJsonValidatingElementVisitor extends JsonElementVisitor {
    private final ProblemsHolder myHolder;
    private final static String MISSING_VALUE = "missingValue";

    public StandardJsonValidatingElementVisitor(ProblemsHolder holder) {myHolder = holder;}

    protected boolean allowComments() { return false; }
    protected boolean allowSingleQuotes() { return false; }
    protected boolean allowIdentifierPropertyNames() { return false; }
    protected boolean allowTrailingCommas() { return false; }

    protected boolean isValidPropertyName(@NotNull PsiElement literal) {
      return literal instanceof JsonLiteral && JsonPsiUtil.getElementTextWithoutHostEscaping(literal).startsWith("\"");
    }

    @Override
    public void visitComment(@NotNull PsiComment comment) {
      if (!allowComments() && myWarnAboutComments) {
        if (JsonStandardComplianceProvider.shouldWarnAboutComment(comment) &&
            comment.getContainingFile().getLanguage() instanceof JsonLanguage) {
          myHolder.registerProblem(comment, JsonBundle.message("inspection.compliance.msg.comments"));
        }
      }
    }

    @Override
    public void visitStringLiteral(@NotNull JsonStringLiteral stringLiteral) {
      if (!allowSingleQuotes() && JsonPsiUtil.getElementTextWithoutHostEscaping(stringLiteral).startsWith("'")) {
        myHolder.registerProblem(stringLiteral, JsonBundle.message("inspection.compliance.msg.single.quoted.strings"),
                                 new AddDoubleQuotesFix());
      }
      // May be illegal property key as well
      super.visitStringLiteral(stringLiteral);
    }

    @Override
    public void visitLiteral(@NotNull JsonLiteral literal) {
      if (JsonPsiUtil.isPropertyKey(literal) && !isValidPropertyName(literal)) {
        myHolder.registerProblem(literal, JsonBundle.message("inspection.compliance.msg.illegal.property.key"), new AddDoubleQuotesFix());
      }

      // for standard JSON, the inspection for NaN, Infinity and -Infinity is now configurable
      if (!allowNanInfinity() && literal instanceof JsonNumberLiteral && myWarnAboutNanInfinity) {
        final String text = JsonPsiUtil.getElementTextWithoutHostEscaping(literal);
        if (StandardJsonLiteralChecker.INF.equals(text) ||
            StandardJsonLiteralChecker.MINUS_INF.equals(text) ||
            StandardJsonLiteralChecker.NAN.equals(text)) {
          myHolder.registerProblem(literal, JsonBundle.message("syntax.error.illegal.floating.point.literal"));
        }
      }
      super.visitLiteral(literal);
    }

    protected boolean allowNanInfinity() {
      return false;
    }

    @Override
    public void visitReferenceExpression(@NotNull JsonReferenceExpression reference) {
      if (!allowIdentifierPropertyNames() || !JsonPsiUtil.isPropertyKey(reference) || !isValidPropertyName(reference)) {
        if (!MISSING_VALUE.equals(reference.getText()) || !InjectedLanguageManager.getInstance(myHolder.getProject()).isInjectedFragment(myHolder.getFile())) {
          myHolder.registerProblem(reference, JsonBundle.message("inspection.compliance.msg.bad.token"), new AddDoubleQuotesFix());
        }
      }
      // May be illegal property key as well
      super.visitReferenceExpression(reference);
    }

    @Override
    public void visitArray(@NotNull JsonArray array) {
      if (myWarnAboutTrailingCommas && !allowTrailingCommas() &&
          JsonStandardComplianceProvider.shouldWarnAboutTrailingComma(array)) {
        final PsiElement trailingComma = findTrailingComma(array, JsonElementTypes.R_BRACKET);
        if (trailingComma != null) {
          myHolder.registerProblem(trailingComma, JsonBundle.message("inspection.compliance.msg.trailing.comma"));
        }
      }
      super.visitArray(array);
    }

    @Override
    public void visitObject(@NotNull JsonObject object) {
      if (myWarnAboutTrailingCommas && !allowTrailingCommas() &&
          JsonStandardComplianceProvider.shouldWarnAboutTrailingComma(object)) {
        final PsiElement trailingComma = findTrailingComma(object, JsonElementTypes.R_CURLY);
        if (trailingComma != null) {
          myHolder.registerProblem(trailingComma, JsonBundle.message("inspection.compliance.msg.trailing.comma"));
        }
      }
      super.visitObject(object);
    }

    @Override
    public void visitValue(@NotNull JsonValue value) {
      if (value.getContainingFile() instanceof JsonFile) {
        final JsonFile jsonFile = (JsonFile)value.getContainingFile();
        if (myWarnAboutMultipleTopLevelValues &&
            value.getParent() == jsonFile &&
            value != jsonFile.getTopLevelValue() &&
            jsonFile.getFileType() != JsonLinesFileType.INSTANCE) {
          myHolder.registerProblem(value, JsonBundle.message("inspection.compliance.msg.multiple.top.level.values"));
        }
      }
    }
  }
}
