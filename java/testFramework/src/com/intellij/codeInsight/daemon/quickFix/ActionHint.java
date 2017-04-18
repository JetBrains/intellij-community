/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.lang.Commenter;
import com.intellij.lang.LanguageCommenters;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiFile;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.Assert.fail;

/**
 * An object representing a wanted assertion of given quick-fix test file.
 *
 * @author Tagir Valeev
 */
public class ActionHint {
  private final String myExpectedText;
  private final boolean myShouldPresent;
  private final ProblemHighlightType myHighlightType;

  private ActionHint(String expectedText, boolean shouldPresent, ProblemHighlightType severity) {
    myExpectedText = expectedText;
    myShouldPresent = shouldPresent;
    myHighlightType = severity;
  }

  /**
   * Returns an expected text of the action.
   * <p>
   * Usage of this method is discouraged: it's not guaranteed that ActionHint actually looks for text.
   * </p>
   *
   * @return an expected action text. May throw an {@link IllegalStateException} if this ActionHint expects something else
   * (e.g. quick-fix of specific type, etc.)
   */
  public String getExpectedText() {
    return myExpectedText;
  }

  /**
   * @return true if this ActionHint checks that some action should be present
   * or false if it checks that some action should be absent
   */
  @SuppressWarnings("WeakerAccess") // used in kotlin
  public boolean shouldPresent() {
    return myShouldPresent;
  }

  /**
   * Finds the action which matches this ActionHint and returns it or returns null
   * if this ActionHint asserts that no action should be present.
   *
   * @param actions actions collection to search inside
   * @param infoSupplier a supplier which provides additional info which will be appended to exception message if check fails
   * @return the action or null
   * @throws AssertionError if no action is found, but it should present, or if action is found, but it should not present.
   */
  @Nullable
  public IntentionAction findAndCheck(Collection<IntentionAction> actions, Supplier<String> infoSupplier) {
    IntentionAction result = actions.stream().filter(t -> t.getText().equals(myExpectedText)).findFirst().orElse(null);
    if(myShouldPresent) {
      if(result == null) {
        fail("Action with text '" + myExpectedText + "' not found\nAvailable actions: " +
             actions.stream().map(IntentionAction::getText).collect(Collectors.joining(", ", "[", "]\n")) +
             infoSupplier.get());
      }
      else if(myHighlightType != null) {
        if (result instanceof IntentionActionDelegate) result = ((IntentionActionDelegate)result).getDelegate();
        if(!(result instanceof QuickFixWrapper)) {
          fail("Action with text '" + myExpectedText + "' is not a LocalQuickFix, but " + result.getClass().getName() +
               "\nExpected LocalQuickFix with ProblemHighlightType=" + myHighlightType + "\n" +
               infoSupplier.get());
        }
        ProblemHighlightType actualType = ((QuickFixWrapper)result).getHighlightType();
        if(actualType != myHighlightType) {
          fail("Action with text '" + myExpectedText + "' has wrong ProblemHighlightType.\nExpected: " + myHighlightType +
               "\nActual: " + actualType + "\n" + infoSupplier.get());
        }
      }
    }
    else if(result != null) {
      fail("Action with text '" + myExpectedText + "' is present, but should not\n" + infoSupplier.get());
    }
    return result;
  }

  /**
   * Parse given file with given contents extracting ActionHint of it.
   * <p>
   * Currently the following syntax is supported:
   * </p>
   * {@code // "quick-fix name or intention text" "true|false|<ProblemHighlightType>"}
   * <p>
   * (replace // with line comment prefix in the corresponding language if necessary).
   * If {@link ProblemHighlightType} enum value is specified instead of true/false
   * (e.g. {@code "INFORMATION"}), then
   * it's expected that the action is present and it's a quick-fix with given highlight type.
   * </p>
   *
   * @param file PsiFile associated with contents (used to determine the language)
   * @param contents file contents
   * @return ActionHint object
   * @throws AssertionError if action hint is absent or has invalid format
   */
  @NotNull
  public static ActionHint parse(@NotNull PsiFile file, @NotNull String contents) {
    PsiFile hostFile = InjectedLanguageManager.getInstance(file.getProject()).getTopLevelFile(file);

    final Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(hostFile.getLanguage());
    String comment = commenter.getLineCommentPrefix();
    if (comment == null) {
      comment = commenter.getBlockCommentPrefix();
    }

    assert comment != null : commenter;
    // "quick fix action text to perform" "should be available"
    Pattern pattern = Pattern.compile("^" + Pattern.quote(comment) + " \"(.*)\" \"(\\w+)\".*", Pattern.DOTALL);
    Matcher matcher = pattern.matcher(contents);
    TestCase.assertTrue("No comment found in " + file.getVirtualFile(), matcher.matches());
    final String text = matcher.group(1);
    String state = matcher.group(2);
    if(state.equals("true") || state.equals("false")) {
      return new ActionHint(text, Boolean.parseBoolean(state), null);
    }
    return new ActionHint(text, true, ProblemHighlightType.valueOf(state));
  }
}
