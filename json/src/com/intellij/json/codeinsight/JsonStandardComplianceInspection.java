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
package com.intellij.json.codeinsight;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.json.JsonBundle;
import com.intellij.json.JsonElementTypes;
import com.intellij.json.psi.*;
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
  private static final Logger LOG = Logger.getInstance(JsonStandardComplianceInspection.class);

  public boolean myWarnAboutComments = true;
  public boolean myWarnAboutMultipleTopLevelValues = true;

  @NotNull
  public String getDisplayName() {
    return JsonBundle.message("inspection.compliance.name");
  }

  @NotNull
  @Override
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JsonElementVisitor() {
      @Override
      public void visitComment(PsiComment comment) {
        if (myWarnAboutComments) {
          if (JsonStandardComplianceProvider.shouldWarnAboutComment(comment)) {
            holder.registerProblem(comment, JsonBundle.message("inspection.compliance.msg.comments"), ProblemHighlightType.WEAK_WARNING);
          }
        }
      }

      @Override
      public void visitStringLiteral(@NotNull JsonStringLiteral stringLiteral) {
        if (JsonPsiUtil.getElementTextWithoutHostEscaping(stringLiteral).startsWith("'")) {
          holder.registerProblem(stringLiteral, JsonBundle.message("inspection.compliance.msg.single.quoted.strings"),
                                 new AddDoubleQuotesFix());
        }
        // May be illegal property key as well
        super.visitStringLiteral(stringLiteral);
      }

      @Override
      public void visitLiteral(@NotNull JsonLiteral literal) {
        if (JsonPsiUtil.isPropertyKey(literal) && !JsonPsiUtil.getElementTextWithoutHostEscaping(literal).startsWith("\"")) {
          holder.registerProblem(literal, JsonBundle.message("inspection.compliance.msg.illegal.property.key"), new AddDoubleQuotesFix());
        }
        super.visitLiteral(literal);
      }

      @Override
      public void visitReferenceExpression(@NotNull JsonReferenceExpression reference) {
        holder.registerProblem(reference, JsonBundle.message("inspection.compliance.msg.bad.token"), new AddDoubleQuotesFix());
        // May be illegal property key as well
        super.visitReferenceExpression(reference);
      }

      @Override
      public void visitArray(@NotNull JsonArray array) {
        final PsiElement trailingComma = findTrailingComma(array, JsonElementTypes.R_BRACKET);
        if (trailingComma != null) {
          holder.registerProblem(trailingComma, JsonBundle.message("inspection.compliance.msg.trailing.comma"));
        }
        super.visitArray(array);
      }

      @Override
      public void visitObject(@NotNull JsonObject object) {
        final PsiElement trailingComma = findTrailingComma(object, JsonElementTypes.R_CURLY);
        if (trailingComma != null) {
          holder.registerProblem(trailingComma, JsonBundle.message("inspection.compliance.msg.trailing.comma"));
        }
        super.visitObject(object);
      }

      @Override
      public void visitValue(@NotNull JsonValue value) {
        if (value.getContainingFile() instanceof JsonFile) {
          final JsonFile jsonFile = (JsonFile)value.getContainingFile();
          if (myWarnAboutMultipleTopLevelValues && value.getParent() == jsonFile && value != jsonFile.getTopLevelValue()) {
            holder.registerProblem(value, JsonBundle.message("inspection.compliance.msg.multiple.top.level.values"));
          }
        }
      }
    };
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
        LOG.error("Quick fix was applied to unexpected element", rawText, element.getParent().getText());
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
}
