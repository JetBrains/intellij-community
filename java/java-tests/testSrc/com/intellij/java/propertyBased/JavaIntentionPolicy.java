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
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.propertyBased.IntentionPolicy;
import org.jetbrains.annotations.NotNull;

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
           actionText.startsWith("Unimplement"); // e.g. leaves red references to the former superclass methods
  }

}

class JavaCommentingStrategy extends JavaIntentionPolicy {
  @Override
  public boolean checkComments(IntentionAction intention) {
    String intentionText = intention.getText();
    boolean commentChangingActions = intentionText.startsWith("Replace with end-of-line comment") ||
                                     intentionText.startsWith("Replace with block comment") ||
                                     intentionText.startsWith("Remove //noinspection") ||
                                     intentionText.startsWith("Unwrap 'if' statement") || //remove ifs content
                                     intentionText.startsWith("Remove 'if' statement") || //remove content of the if with everything inside
                                     intentionText.startsWith("Unimplement Class") || intentionText.startsWith("Unimplement Interface") || //remove methods in batch
                                     intentionText.startsWith("Suppress with 'NON-NLS' comment") ||
                                     intentionText.startsWith("Move comment to separate line") || //merge comments on same line
                                     intentionText.startsWith("Remove redundant arguments to call") || //removes arg with all comments inside
                                     intentionText.startsWith("Convert to 'enum'") || //removes constructor with javadoc?
                                     intentionText.startsWith("Replace 'switch' with 'if'") || //todo IDEA-113518
                                     intentionText.startsWith("Remove redundant constructor") ||
                                     intentionText.startsWith("Remove block marker comments") ||
                                     intentionText.startsWith("Remove redundant method") ||
                                     intentionText.startsWith("Delete unnecessary import") ||
                                     intentionText.startsWith("Replace with 'throws Exception'") ||
                                     intentionText.startsWith("Replace unicode escape with character") ||
                                     intentionText.startsWith("Remove 'serialVersionUID' field") ||
                                     intentionText.startsWith("Remove unnecessary") ||
                                     intentionText.contains("'ordering inconsistent with equals'") || //javadoc will be changed
                                     intentionText.matches("Simplify '.*' to .*") ||
                                     intentionText.matches("Move '.*' to Javadoc ''@throws'' tag")
      ;
    return !commentChangingActions;
  }

  @Override
  public boolean trackComment(PsiComment comment) {
    return PsiTreeUtil.getParentOfType(comment, PsiImportList.class) == null;
  }

  @Override
  public boolean mayBreakCode(@NotNull IntentionAction action, @NotNull Editor editor, @NotNull PsiFile file) {
    return true;
  }
}

class JavaGreenIntentionPolicy extends JavaIntentionPolicy {

  @Override
  protected boolean shouldSkipIntention(@NotNull String actionText) {
    return super.shouldSkipIntention(actionText) || mayBreakCompilation(actionText);
  }
}
