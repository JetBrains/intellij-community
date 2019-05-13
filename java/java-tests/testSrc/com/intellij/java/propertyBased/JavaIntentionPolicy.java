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

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.propertyBased.IntentionPolicy;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author peter
 */
class JavaIntentionPolicy extends IntentionPolicy {
  @Override
  protected boolean shouldSkipIntention(@NotNull String actionText) {
    return actionText.startsWith("Generate empty 'private' constructor") || // displays a dialog
           actionText.startsWith("Attach annotations") || // changes project model
           actionText.startsWith("Change class type parameter") || // doesn't change file text (starts live template)
           actionText.startsWith("Rename reference") || // doesn't change file text (starts live template)
           actionText.equals("Remove") || // IDEA-177220
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
           actionText.startsWith("Unwrap 'else' branch (changes semantics)") || // might produce code with final variables are initialized several times
           actionText.startsWith("Create missing 'switch' branches") || // if all existing branches do 'return something', we don't automatically generate compilable code for new branches
           actionText.matches("Make .* default") || // can make interface non-functional and its lambdas incorrect
           actionText.startsWith("Unimplement") || // e.g. leaves red references to the former superclass methods
           actionText.startsWith("Add 'catch' clause for '") || // if existing catch contains "return value", new error "Missing return statement" may appear
           actionText.startsWith("Surround with try-with-resources block") || // if 'close' throws, we don't add a new 'catch' for that, see IDEA-196544
           actionText.equals("Split into declaration and initialization") || // TODO: remove when IDEA-179081 is fixed
           //may break catches with explicit exceptions
           actionText.matches("Replace with throws .*") ||
           actionText.matches("Replace with '(new .+\\[]|.+\\[]::new)'"); // Suspicious toArray may introduce compilation error
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
    boolean isCommentChangingAction = intentionText.startsWith("Replace with end-of-line comment") ||
                                     intentionText.startsWith("Replace with block comment") ||
                                     intentionText.startsWith("Remove //noinspection") ||
                                     intentionText.startsWith("Unwrap 'if' statement") || //remove ifs content
                                     intentionText.startsWith("Remove 'if' statement") || //remove content of the if with everything inside
                                     intentionText.startsWith("Unimplement Class") || intentionText.startsWith("Unimplement Interface") || //remove methods in batch
                                     intentionText.startsWith("Suppress with 'NON-NLS' comment") ||
                                     intentionText.startsWith("Move comment to separate line") || //merge comments on same line
                                     intentionText.startsWith("Remove redundant arguments to call") || //removes arg with all comments inside
                                     intentionText.startsWith("Convert to 'enum'") || //removes constructor with javadoc?
                                     intentionText.startsWith("Remove redundant constructor") ||
                                     intentionText.startsWith("Remove block marker comments") ||
                                     intentionText.startsWith("Remove redundant method") ||
                                     intentionText.startsWith("Delete unnecessary import") ||
                                     intentionText.startsWith("Delete empty class initializer") ||
                                     intentionText.startsWith("Replace with 'throws Exception'") ||
                                     intentionText.startsWith("Replace unicode escape with character") ||
                                     intentionText.startsWith("Remove 'serialVersionUID' field") ||
                                     intentionText.startsWith("Remove unnecessary") ||
                                     intentionText.startsWith("Remove 'try-finally' block") ||
                                     intentionText.startsWith("Fix doc comment") ||
                                     intentionText.startsWith("Add Javadoc") ||
                                     intentionText.startsWith("Replace qualified name with import") || //may change references in javadoc, making refs always qualified in javadoc makes them expand on "reformat"
                                     intentionText.startsWith("Qualify with outer class") || // may change links in javadoc
                                     intentionText.contains("'ordering inconsistent with equals'") || //javadoc will be changed
                                     intentionText.matches("Simplify '.*' to .*") ||
                                     intentionText.matches("Move '.*' to Javadoc ''@throws'' tag") ||
                                     intentionText.matches("Remove '.*' from '.*' throws list") ||
                                     intentionText.matches("Remove .+ suppression");
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
  protected boolean shouldSkipIntention(@NotNull String actionText) {
    return super.shouldSkipIntention(actionText) || mayBreakCompilation(actionText);
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
           super.shouldSkipIntention(actionText);
  }

  @Override
  protected boolean shouldSkipByFamilyName(@NotNull String familyName) {
    return // if((a && b)) -- extract "a" doesn't work, seems legit, remove parentheses first
      familyName.equals("Extract If Condition") ||
      // Cutting the message at different points is possible like
      // "Simplify 'foo || bar || baz || ...' to false" and "Simplify 'foo || (bar) || baz ...' to false"
      familyName.equals("Simplify boolean expression");
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
          parent instanceof PsiBreakStatement && ((PsiBreakStatement)parent).getLabelExpression() != null) {
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
