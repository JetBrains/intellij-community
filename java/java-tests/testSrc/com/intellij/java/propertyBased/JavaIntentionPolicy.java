// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.propertyBased;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModShowConflicts;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.propertyBased.IntentionPolicy;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

class JavaIntentionPolicy extends IntentionPolicy {
  @Override
  protected boolean shouldSkipIntention(@NotNull String actionText) {
    return actionText.startsWith("Generate empty 'private' constructor") || // displays a dialog
           actionText.startsWith("Attach annotations") || // changes project model
           actionText.startsWith("Change class type parameter") || // doesn't change file text (starts live template)
           actionText.startsWith("Rename reference") || // doesn't change file text (starts live template)
           actionText.equals("Reformat file") || // ProblematicWhitespaceInspection: may do nothing when problematic whitespace is inside comment, related to IDEA-305318
           super.shouldSkipIntention(actionText);
  }

  @Override
  public boolean mayBreakCode(@NotNull IntentionAction action, @NotNull Editor editor, @NotNull PsiFile file) {
    return mayBreakCompilation(action.getText()) || requiresRealJdk(action, file);
  }

  @Override
  public boolean shouldTolerateIntroducedError(@NotNull HighlightInfo info) {
    return info.getText().contains("is public, should be declared in a file named"); // https://youtrack.jetbrains.com/issue/IDEA-196018
  }

  private static boolean requiresRealJdk(@NotNull IntentionAction action, @NotNull PsiFile file) {
    return action.getText().contains("java.text.MessageFormat") && 
           JavaPsiFacade.getInstance(file.getProject()).findClass("java.text.MessageFormat", file.getResolveScope()) == null;
  }

  protected static boolean mayBreakCompilation(String actionText) {
    return actionText.startsWith("Flip") || // doesn't care about compilability
           actionText.startsWith("Convert to string literal") || // can produce uncompilable code by design
           actionText.startsWith("Replace string literal with character") || // can produce uncompilable code by design
           actionText.startsWith("Detail exceptions") || // can produce uncompilable code if 'catch' section contains 'instanceof's
           actionText.startsWith("Insert call to super method") || // super method can declare checked exceptions, unexpected at this point
           actionText.startsWith("Cast to ") || // produces uncompilable code by design
           actionText.matches("Surround with 'if \\(.+\\)'") || // might produce uninitialized variable or missing return statement problem
           actionText.startsWith("Unwrap 'else' branch (changes semantics)") || // might produce code with final variables are initialized several times
           actionText.startsWith("Create missing branches: ") || // if all existing branches do 'return something', we don't automatically generate compilable code for new branches
           actionText.matches("Make .* default") || // can make interface non-functional and its lambdas incorrect
           actionText.startsWith("Unimplement") || // e.g. leaves red references to the former superclass methods
           actionText.startsWith("Add 'catch' clause for '") || // if existing catch contains "return value", new error "Missing return statement" may appear
           actionText.startsWith("Surround with try-with-resources block") || // if 'close' throws, we don't add a new 'catch' for that, see IDEA-196544
           actionText.equals("Split into declaration and initialization") || // TODO: remove when IDEA-179081 is fixed
           actionText.matches("Replace with throws .*") || //may break catches with explicit exceptions
           actionText.equals("Generate 'clone()' method which always throws exception") || // IDEA-207048
           actionText.matches("Replace '.+' with '.+' in cast") || // can produce uncompilable code by design
           actionText.matches("Replace with '(new .+\\[]|.+\\[]::new)'") || // Suspicious toArray may introduce compilation error
           actionText.equals("Rollback changes in current line"); //revert only one line
  }

  static boolean skipPreview(@NotNull IntentionAction action) {
    String familyName = action.getFamilyName();
    return familyName.matches("(?i)Create \\w+ from usage") ||
           familyName.equals("Create Constructor") ||
           // Does not change file content
           familyName.equals("Rename File") ||
           familyName.equals("Move file to a source root");
  }
}

class JavaCommentingStrategy extends JavaIntentionPolicy {
  @Override
  protected boolean shouldSkipIntention(@NotNull String actionText) {
    return actionText.startsWith("Fix doc comment") || //change formatting settings
           actionText.startsWith("Add Javadoc") ||
           super.shouldSkipIntention(actionText);
  }

  @Override
  public boolean checkComments(IntentionAction intention) {
    String intentionText = intention.getText();
    String familyName = intention.getFamilyName();
    boolean isCommentChangingAction = intentionText.startsWith("Replace with end-of-line comment") ||
                                      intentionText.startsWith("Replace with block comment") ||
                                      intentionText.equals("Replace with Javadoc comment") ||
                                      intentionText.startsWith("Remove //noinspection") ||
                                      intentionText.startsWith("Convert to Basic Latin") ||
                                      intentionText.startsWith("Unwrap 'if' statement") ||//remove ifs content
                                      intentionText.startsWith("Remove 'if' statement") ||//remove content of the if with everything inside
                                      intentionText.equals("Remove 'while' statement") ||
                                      intentionText.startsWith("Unimplement Class") || intentionText.startsWith("Unimplement Interface") ||//remove methods in batch
                                      intentionText.startsWith("Suppress with 'NON-NLS' comment") ||
                                      intentionText.startsWith("Suppress for ") || // Suppressions often modify comments 
                                      intentionText.startsWith("Move comment to separate line") ||//merge comments on same line
                                      intentionText.startsWith("Remove redundant arguments to call") ||//removes arg with all comments inside
                                      intentionText.startsWith("Convert to 'enum'") ||//removes constructor with javadoc?
                                      intentionText.startsWith("Remove redundant constructor") ||
                                      intentionText.startsWith("Remove block marker comment") ||
                                      intentionText.startsWith("Remove redundant method") ||
                                      intentionText.startsWith("Delete unnecessary import") ||
                                      intentionText.startsWith("Delete empty class initializer") ||
                                      intentionText.startsWith("Replace with 'throws Exception'") ||
                                      intentionText.equals("Replace 'catch' section with 'throws' declaration") ||
                                      intentionText.startsWith("Replace unicode escape with character") ||
                                      intentionText.startsWith("Remove 'serialVersionUID' field") ||
                                      intentionText.startsWith("Remove unnecessary") ||
                                      intentionText.startsWith("Remove 'try-finally' block") ||
                                      intentionText.startsWith("Fix doc comment") ||
                                      intentionText.startsWith("Add Javadoc") ||
                                      intentionText.startsWith("Replace qualified name with import") ||//may change references in javadoc, making refs always qualified in javadoc makes them expand on "reformat"
                                      intentionText.startsWith("Qualify with outer class") ||// may change links in javadoc
                                      intentionText.contains("'ordering inconsistent with equals'") ||//javadoc will be changed
                                      intentionText.matches("Simplify '.*' to .*") ||
                                      intentionText.matches("Move '.*' to Javadoc ''@throws'' tag") ||
                                      intentionText.matches("Remove '.*' from '.*' throws list") ||
                                      intentionText.matches(JavaAnalysisBundle.message("inspection.redundant.type.remove.quickfix")) ||
                                      intentionText.matches("Remove .+ suppression") ||
                                      familyName.equals("Fix typo") ||
                                      familyName.equals("Remove annotation") || // may remove comment inside annotation
                                      familyName.equals("Reformat the whole file"); // may update @noinspection lines
    return !isCommentChangingAction;
  }

  @Override
  public boolean trackComment(PsiComment comment) {
    return PsiTreeUtil.getParentOfType(comment, PsiImportList.class) == null;
  }

  @Override
  public boolean mayBreakCode(@NotNull IntentionAction action, @NotNull Editor editor, @NotNull PsiFile file) {
    return true;
  }

  @Override
  protected boolean shouldSkipByFamilyName(@NotNull String familyName) {
    return 
      //changes javadoc explicitly
      familyName.equals("Move to Javadoc '@throws'");
  }
}

class JavaGreenIntentionPolicy extends JavaIntentionPolicy {
  @Override
  protected boolean shouldCheckPreview(@NotNull IntentionAction action) {
    return !skipPreview(action);
  }

  @Override
  protected boolean shouldSkipIntention(@NotNull String actionText) {
    return super.shouldSkipIntention(actionText) || mayBreakCompilation(actionText);
  }

  @Override
  public @Nullable String validateCommand(@NotNull ModCommand modCommand) {
    if (modCommand instanceof ModShowConflicts conflicts) {
      return "Conflict; may break compilation: " +
             conflicts.conflicts().values().stream().flatMap(c -> c.messages().stream()).distinct().collect(Collectors.joining("; "));
    }
    return super.validateCommand(modCommand);
  }
}

class JavaParenthesesPolicy extends JavaIntentionPolicy {

  @Override
  protected boolean shouldSkipIntention(@NotNull String actionText) {
    return actionText.equals("Add clarifying parentheses") ||
           actionText.equals("Remove unnecessary parentheses") ||
           actionText.equals("See other similar duplicates") ||
           actionText.equals("Replace character literal with string") ||
           actionText.matches("Simplify '\\(+(true|false)\\)+' to \\1") ||
           // Parenthesizing sub-expression causes cutting the action name at different position, so name changes significantly
           actionText.matches("Compute constant value of '.+'") ||
           actionText.matches("Replace '.+' with constant value '.+'") ||
           // TODO: Remove when IDEA-195235 is fixed
           actionText.matches("Suppress .+ in injection") ||
           actionText.equals("Suppress with 'NON-NLS' comment") || // IDEA-218088
           super.shouldSkipIntention(actionText);
  }

  @Override
  protected boolean shouldSkipByFamilyName(@NotNull String familyName) {
    return // if((a && b)) -- extract "a" doesn't work, seems legit, remove parentheses first
      familyName.equals("Extract 'if' condition") ||
      // Cutting the message at different points is possible like
      // "Simplify 'foo || bar || baz || ...' to false" and "Simplify 'foo || (bar) || baz ...' to false"
      familyName.equals("Simplify boolean expression") ||
      // A parenthesized enum switch case label is a compilation error
      familyName.equals("Create missing enum switch branches") ||
      familyName.equals("Reformat the whole file") ||
      familyName.equals("Fix whitespace") ||
      // For some reason, these intentions cause many unstable results.
      familyName.equals("Put elements on multiple lines") || familyName.equals("Put elements on one line");
  }

  @NotNull
  @Override
  public List<PsiElement> getElementsToWrap(@NotNull PsiElement element) {
    List<PsiElement> result = new ArrayList<>();
    while (true) {
      PsiExpression expression = PsiTreeUtil.getNonStrictParentOfType(element, PsiExpression.class);
      if (expression == null) break;
      if (PsiTreeUtil.getParentOfType(expression, PsiAnnotationMethod.class, true, PsiStatement.class) != null) {
        break;
      }
      while (shouldParenthesizeParent(expression)) {
        expression = (PsiExpression)expression.getParent();
      }
      PsiElement parent = expression.getParent();
      if (ExpressionUtils.isVoidContext(expression) ||
          parent instanceof PsiNameValuePair ||
          parent instanceof PsiArrayInitializerMemberValue ||
          parent instanceof PsiExpressionList && parent.getParent() instanceof PsiSwitchLabelStatementBase ||
          parent instanceof PsiBreakStatement && ((PsiBreakStatement)parent).getLabelIdentifier() != null) {
        break;
      }
      if (parent instanceof PsiVariable && expression instanceof PsiArrayInitializerExpression) break;
      result.add(expression);
      element = expression.getParent();
    }
    return result;
  }

  @NotNull
  @Override
  public String getWrapPrefix() {
    return "(";
  }

  @Override
  public String getWrapSuffix() {
    return ")";
  }

  private static boolean shouldParenthesizeParent(PsiExpression expression) {
    PsiElement parent = expression.getParent();
    if (parent instanceof PsiCallExpression) {
      return true;
    }
    if (expression instanceof PsiReferenceExpression && parent instanceof PsiReferenceExpression) {
      PsiElement target = ((PsiReferenceExpression)expression).resolve();
      if (target instanceof PsiPackage || target instanceof PsiClass) {
        return true;
      }
      if (target == null) {
        // unresolved qualifier: if it's just reference chain like a.b.c it could be inaccessible package, so let's avoid parenthesizing it
        PsiExpression qualifier = expression;
        while (qualifier instanceof PsiReferenceExpression) {
          qualifier = ((PsiReferenceExpression)qualifier).getQualifierExpression();
        }
        return qualifier == null;
      }
    }
    if (expression instanceof PsiArrayInitializerExpression && parent instanceof PsiArrayInitializerExpression) {
      return true;
    }
    if (expression instanceof PsiSuperExpression) {
      return true;
    }
    return false;
  }
}
class JavaPreviewIntentionPolicy extends JavaIntentionPolicy {
  @Override
  protected boolean shouldCheckPreview(@NotNull IntentionAction action) {
    return !skipPreview(action);
  }
}