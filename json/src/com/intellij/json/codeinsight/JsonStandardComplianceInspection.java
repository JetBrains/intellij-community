package com.intellij.json.codeinsight;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.json.JsonBundle;
import com.intellij.json.JsonElementTypes;
import com.intellij.json.psi.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiWhiteSpace;
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
 * </ul>
 *
 * @author Mikhail Golubev
 */
public class JsonStandardComplianceInspection extends LocalInspectionTool {
  private static final Logger LOG = Logger.getInstance(JsonStandardComplianceInspection.class);

  public boolean myWarnAboutComments = true;

  @NotNull
  public String getDisplayName() {
    return JsonBundle.message("name.standard.compliance.inspection");
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
            holder.registerProblem(comment, JsonBundle.message("msg.compliance.problem.comments"), ProblemHighlightType.WEAK_WARNING);
          }
        }
      }

      @Override
      public void visitStringLiteral(@NotNull JsonStringLiteral stringLiteral) {
        if (JsonPsiUtil.getElementTextWithoutHostEscaping(stringLiteral).startsWith("'")) {
          holder.registerProblem(stringLiteral, JsonBundle.message("msg.compliance.problem.single.quoted.strings"),
                                 new AddDoubleQuotesFix());
        }
        // May be illegal property key as well
        super.visitStringLiteral(stringLiteral);
      }

      @Override
      public void visitLiteral(@NotNull JsonLiteral literal) {
        if (JsonPsiUtil.isPropertyKey(literal) && !JsonPsiUtil.getElementTextWithoutHostEscaping(literal).startsWith("\"")) {
          holder.registerProblem(literal, JsonBundle.message("msg.compliance.problem.illegal.property.key"), new AddDoubleQuotesFix());
        }
      }

      @Override
      public void visitReferenceExpression(@NotNull JsonReferenceExpression reference) {
        holder.registerProblem(reference, JsonBundle.message("msg.compliance.problem.identifier"), new AddDoubleQuotesFix());
        // May be illegal property key as well
        super.visitReferenceExpression(reference);
      }

      @Override
      public void visitArray(@NotNull JsonArray array) {
        final PsiElement trailingComma = findTrailingComma(array, JsonElementTypes.R_BRACKET);
        if (trailingComma != null) {
          holder.registerProblem(trailingComma, JsonBundle.message("msg.compliance.problem.trailing.comma"));
        }
      }

      @Override
      public void visitObject(@NotNull JsonObject object) {
        final PsiElement trailingComma = findTrailingComma(object, JsonElementTypes.R_CURLY);
        if (trailingComma != null) {
          holder.registerProblem(trailingComma, JsonBundle.message("msg.compliance.problem.trailing.comma"));
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
    final PsiElement beforeEnding = PsiTreeUtil.skipSiblingsBackward(lastChild, PsiComment.class, PsiWhiteSpace.class);
    if (beforeEnding != null && beforeEnding.getNode().getElementType() == JsonElementTypes.COMMA) {
      return beforeEnding;
    }
    return null;
  }


  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);
    optionsPanel.addCheckbox(JsonBundle.message("option.warn.about.comments.name"), "myWarnAboutComments");
    return optionsPanel;
  }

  private static class AddDoubleQuotesFix implements LocalQuickFix {
    @NotNull
    @Override
    public String getName() {
      return JsonBundle.message("name.add.double.quotes.quickfix");
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
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
        // TODO: find out better way to replace element and skip reformatting step afterwards
        final ASTNode replacement = new JsonElementGenerator(project).createValue("\"" + content + "\"").getNode();
        element.getParent().getNode().replaceChild(element.getNode(), replacement);
      }
      else if (element != null) {
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
