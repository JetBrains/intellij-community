// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.regexp.intention;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.intellij.lang.annotations.Language;
import org.intellij.lang.regexp.RegExpFileType;

import javax.swing.tree.TreeNode;

/**
 * @author Bas Leijdekkers
 */
public final class ExplainRegExpIntentionTest extends BasePlatformTestCase {

  public void testSimple() {
    doTest("faceteous|hackneyed",
           """
             faceteous|hackneyed Alternation (https://www.regular-expressions.info/alternation.html) – matches 1 of 2 alternatives
               faceteous – matches characters in order
                 f – matches the LATIN SMALL LETTER F character
                 a – matches the LATIN SMALL LETTER A character
                 c – matches the LATIN SMALL LETTER C character
                 e – matches the LATIN SMALL LETTER E character
                 t – matches the LATIN SMALL LETTER T character
                 e – matches the LATIN SMALL LETTER E character
                 o – matches the LATIN SMALL LETTER O character
                 u – matches the LATIN SMALL LETTER U character
                 s – matches the LATIN SMALL LETTER S character
               hackneyed – matches characters in order
                 h – matches the LATIN SMALL LETTER H character
                 a – matches the LATIN SMALL LETTER A character
                 c – matches the LATIN SMALL LETTER C character
                 k – matches the LATIN SMALL LETTER K character
                 n – matches the LATIN SMALL LETTER N character
                 e – matches the LATIN SMALL LETTER E character
                 y – matches the LATIN SMALL LETTER Y character
                 e – matches the LATIN SMALL LETTER E character
                 d – matches the LATIN SMALL LETTER D character
             """);
  }
  
  public void testEscapes() {
    doTest("\041 \u0032\\041 \\u0032\\n\\w\\c@\\x61\\e",
           """
             ! 2\\041 \\u0032\\n\\w\\c@\\x61\\e – matches elements in order
               ! – matches the EXCLAMATION MARK character
                 – matches the SPACE character
               2 – matches the DIGIT TWO character
               \\041 Octal Escape (https://www.regular-expressions.info/nonprint.html#octal) – matches the EXCLAMATION MARK (!) character
                 – matches the SPACE character
               \\u0032 Unicode Escape (https://www.regular-expressions.info/nonprint.html) – matches the DIGIT TWO (2) character
               \\n Escape Character (https://www.regular-expressions.info/nonprint.html) – matches the LINE FEED (LF) character
               \\w Shorthand Character Class (https://www.regular-expressions.info/shorthand.html) – matches a word character (letter, digit or underscore)
               \\c@ Control Character Escape (https://www.regular-expressions.info/nonprint.html) – matches the NULL character
               \\x61 Hexadecimal Escape (https://www.regular-expressions.info/nonprint.html) – matches the LATIN SMALL LETTER A (a) character
               \\e Escape Character (https://www.regular-expressions.info/nonprint.html) – matches the ESCAPE character
             """);
  }

  public void testJustCharacters() {
    doTest("\\(vitreous humour\\)",
           """
             \\(vitreous humour\\) – matches elements in order
               \\( – matches the LEFT PARENTHESIS character
               vitreous – matches characters in order
                 v – matches the LATIN SMALL LETTER V character
                 i – matches the LATIN SMALL LETTER I character
                 t – matches the LATIN SMALL LETTER T character
                 r – matches the LATIN SMALL LETTER R character
                 e – matches the LATIN SMALL LETTER E character
                 o – matches the LATIN SMALL LETTER O character
                 u – matches the LATIN SMALL LETTER U character
                 s – matches the LATIN SMALL LETTER S character
                 – matches the SPACE character
               humour – matches characters in order
                 h – matches the LATIN SMALL LETTER H character
                 u – matches the LATIN SMALL LETTER U character
                 m – matches the LATIN SMALL LETTER M character
                 o – matches the LATIN SMALL LETTER O character
                 u – matches the LATIN SMALL LETTER U character
                 r – matches the LATIN SMALL LETTER R character
               \\) – matches the RIGHT PARENTHESIS character
             """);
  }

  public void testAlternation() {
    doTest("(\\( \\b|\\b \\))",
           """
             (\\( \\b|\\b \\)) Capturing Group (https://www.regular-expressions.info/brackets.html) – #1 stores the text it matches for later reference
               \\( \\b|\\b \\) Alternation (https://www.regular-expressions.info/alternation.html) – matches 1 of 2 alternatives
                 \\( \\b – matches elements in order
                   \\( – matches the LEFT PARENTHESIS character
                     – matches the SPACE character
                   \\b Word Boundary (https://www.regular-expressions.info/wordboundaries.html) – matches between a word character and a non-word character
                 \\b \\) – matches elements in order
                   \\b Word Boundary (https://www.regular-expressions.info/wordboundaries.html) – matches between a word character and a non-word character
                     – matches the SPACE character
                   \\) – matches the RIGHT PARENTHESIS character
             """);
  }

  public void testExactlyNTimes() {
    Registry.get("explain.regexp.intention.nested.quantifiers").setValue(true, myFixture.getTestRootDisposable());
    doTest("[0-9]{3}-[0-9]{4}",
           """
             [0-9]{3}-[0-9]{4} – matches elements in order
               [0-9]{3} Quantifier (https://www.regular-expressions.info/repeat.html) – matches exactly 3 times
                 [0-9] Character Class (https://www.regular-expressions.info/charclass.html) – matches 1 character from DIGIT ZERO to DIGIT NINE (10 characters)
                   0-9 Range (https://www.regular-expressions.info/charclass.html) – matches 1 character from DIGIT ZERO to DIGIT NINE (10 characters)
               - – matches the HYPHEN-MINUS character
               [0-9]{4} Quantifier (https://www.regular-expressions.info/repeat.html) – matches exactly 4 times
                 [0-9] Character Class (https://www.regular-expressions.info/charclass.html) – matches 1 character from DIGIT ZERO to DIGIT NINE (10 characters)
                   0-9 Range (https://www.regular-expressions.info/charclass.html) – matches 1 character from DIGIT ZERO to DIGIT NINE (10 characters)
             """);
  }

  public void testExactlyNTimesFlat() {
    Registry.get("explain.regexp.intention.nested.quantifiers").setValue(false, myFixture.getTestRootDisposable());
    doTest("[0-9]{3}-[0-9]{4}",
           """
             [0-9]{3}-[0-9]{4} – matches elements in order
               [0-9] Character Class (https://www.regular-expressions.info/charclass.html) – matches 1 character from DIGIT ZERO to DIGIT NINE (10 characters)
                 0-9 Range (https://www.regular-expressions.info/charclass.html) – matches 1 character from DIGIT ZERO to DIGIT NINE (10 characters)
               {3} Quantifier (https://www.regular-expressions.info/repeat.html) – matches the previous element exactly 3 times
               - – matches the HYPHEN-MINUS character
               [0-9] Character Class (https://www.regular-expressions.info/charclass.html) – matches 1 character from DIGIT ZERO to DIGIT NINE (10 characters)
                 0-9 Range (https://www.regular-expressions.info/charclass.html) – matches 1 character from DIGIT ZERO to DIGIT NINE (10 characters)
               {4} Quantifier (https://www.regular-expressions.info/repeat.html) – matches the previous element exactly 4 times
             """);
  }

  public void testComment() {
    doTest("(?x)  implausible# inconceivable",
           """
             (?x)  implausible – matches elements in order
               (?x) Inline Mode Modifier (https://www.regular-expressions.info/modifiers.html) – turns regex modes on or off
                 x – turns on comments mode
               implausible – matches characters in order
                 i – matches the LATIN SMALL LETTER I character
                 m – matches the LATIN SMALL LETTER M character
                 p – matches the LATIN SMALL LETTER P character
                 l – matches the LATIN SMALL LETTER L character
                 a – matches the LATIN SMALL LETTER A character
                 u – matches the LATIN SMALL LETTER U character
                 s – matches the LATIN SMALL LETTER S character
                 i – matches the LATIN SMALL LETTER I character
                 b – matches the LATIN SMALL LETTER B character
                 l – matches the LATIN SMALL LETTER L character
                 e – matches the LATIN SMALL LETTER E character
             # inconceivable Comment (https://www.regular-expressions.info/freespacing.html)
             """);
  }

  private void doTest(@Language("RegExp") String regexp, String result) {
    PsiFile file = myFixture.configureByText(RegExpFileType.INSTANCE, regexp);
    assertEquals(result, toString(ExplainRegExpIntention.buildExplanationTree(file)));
  }

  private static String toString(TreeNode node) {
    return buildString(node, -1, new StringBuilder()).toString();
  }

  private static StringBuilder buildString(TreeNode node, int depth, StringBuilder out) {
    if (depth >= 0) out.append(StringUtil.repeat("  ", depth)).append(node).append('\n');
    int count = node.getChildCount();
    depth++;
    for (int i = 0; i < count; i++) {
      buildString(node.getChildAt(i), depth, out);
    }
    return out;
  }
}
