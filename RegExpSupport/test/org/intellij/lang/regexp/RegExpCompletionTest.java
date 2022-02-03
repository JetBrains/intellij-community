// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.regexp;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.application.PathManager;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RegExpCompletionTest extends CodeInsightFixtureTestCase {

  // util methods
    private static String getInputDataFileName(String testName) {
        return Character.toUpperCase(testName.charAt(0)) + testName.substring(1) + ".regexp";
    }

    private static String getExpectedResultFileName(String testName) {
        return Character.toUpperCase(testName.charAt(0)) + testName.substring(1) + "Expected" + ".regexp";
    }

    public void testNamedCharacter() {
      myFixture.configureByText(RegExpFileType.INSTANCE, "\\N{SMILE<caret>}");
      final LookupElement[] elements = myFixture.completeBasic();
      final List<String> strings = ContainerUtil.map(elements, LookupElement::getLookupString);
      List<String> alwaysPresent = Arrays.asList("SMILE", "SMILING FACE WITH SMILING EYES", "SMILING FACE WITH HEART-SHAPED EYES",
                                                 "SMILING CAT FACE WITH HEART-SHAPED EYES", "SMILING FACE WITH OPEN MOUTH AND SMILING EYES",
                                                 "SMILING FACE WITH OPEN MOUTH AND TIGHTLY-CLOSED EYES", "CAT FACE WITH WRY SMILE",
                                                 "GRINNING CAT FACE WITH SMILING EYES", "GRINNING FACE WITH SMILING EYES",
                                                 "KISSING FACE WITH SMILING EYES",
                                                 // Unicode 10.0
                                                 "SMILING FACE WITH SMILING EYES AND HAND COVERING MOUTH",
                                                 "SIGNWRITING MOUTH SMILE", "SIGNWRITING MOUTH SMILE OPEN",
                                                 "SIGNWRITING MOUTH SMILE WRINKLED");
      String message = strings.toString();
      assertTrue(message, strings.containsAll(alwaysPresent));
      List<String> other = new ArrayList<>(strings);
      other.removeAll(alwaysPresent);
      List<String> maybePresent = Arrays.asList(
        // Unicode 11.0
        "SMILING FACE WITH SMILING EYES AND THREE HEARTS",
        // Unicode 13.0
        "CHORASMIAN LETTER ALEPH", "CHORASMIAN LETTER AYIN", "CHORASMIAN LETTER BETH", "CHORASMIAN LETTER CURLED WAW",
        "CHORASMIAN LETTER DALETH", "CHORASMIAN LETTER GIMEL", "CHORASMIAN LETTER HE", "CHORASMIAN LETTER HETH", "CHORASMIAN LETTER KAPH",
        "CHORASMIAN LETTER LAMEDH", "CHORASMIAN LETTER MEM", "CHORASMIAN LETTER NUN", "CHORASMIAN LETTER PE", "CHORASMIAN LETTER RESH",
        "CHORASMIAN LETTER SAMEKH", "CHORASMIAN LETTER SHIN", "CHORASMIAN LETTER SMALL ALEPH", "CHORASMIAN LETTER TAW",
        "CHORASMIAN LETTER WAW", "CHORASMIAN LETTER YODH", "CHORASMIAN LETTER ZAYIN");
      assertTrue(message, maybePresent.containsAll(other));
    }

    public void testBackSlashVariants() {
        List<String> nameList =
          new ArrayList<>(Arrays.asList("d", "D", "s", "S", "w", "W", "b", "B", "A", "G", "Z", "z", "Q", "E",
                                        "t", "n", "r", "f", "a", "e", "h", "H", "v", "V", "R", "X", "b{g}"));
        for (String[] stringArray : DefaultRegExpPropertiesProvider.getInstance().getAllKnownProperties()) {
            nameList.add("p{" + stringArray[0] + "}");
        }
      myFixture.testCompletionVariants(getInputDataFileName(getTestName(true)), ArrayUtilRt.toStringArray(nameList));
    }

    public void testPropertyVariants() {
        List<String> nameList = new ArrayList<>();
        for (String[] stringArray : DefaultRegExpPropertiesProvider.getInstance().getAllKnownProperties()) {
            nameList.add("{" + stringArray[0] + "}");
        }
      myFixture.testCompletionVariants(getInputDataFileName(getTestName(true)), ArrayUtilRt.toStringArray(nameList));
    }

    public void testPropertyAlpha() {
      myFixture.configureByText(RegExpFileType.INSTANCE, "\\P{Alp<caret>}");
      myFixture.completeBasic();
      myFixture.checkResult("\\P{Alpha<caret>}");
    }

    public void doTest() {
        String inputDataFileName = getInputDataFileName(getTestName(true));
        String expectedResultFileName = getExpectedResultFileName(getTestName(true));
        myFixture.testCompletion(inputDataFileName, expectedResultFileName);
    }

    @Override
    protected String getBasePath() {
      String homePath = PathManager.getHomePath();
      File candidate = new File(homePath, "community/RegExpSupport/testData/completion");
      if (candidate.isDirectory()) {
        return "/community/RegExpSupport/testData/completion";
      }
      return "/RegExpSupport/testData/completion";
    }
}
