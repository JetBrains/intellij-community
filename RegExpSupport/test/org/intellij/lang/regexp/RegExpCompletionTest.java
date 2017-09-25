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
package org.intellij.lang.regexp;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.application.PathManager;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import com.intellij.util.ArrayUtil;
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
      assertEquals(Arrays.asList("SMILE", "SMILING FACE WITH SMILING EYES", "SMILING FACE WITH HEART-SHAPED EYES",
                                 "SMILING CAT FACE WITH HEART-SHAPED EYES", "SMILING FACE WITH OPEN MOUTH AND SMILING EYES",
                                 "SMILING FACE WITH OPEN MOUTH AND TIGHTLY-CLOSED EYES", "CAT FACE WITH WRY SMILE",
                                 "GRINNING CAT FACE WITH SMILING EYES", "GRINNING FACE WITH SMILING EYES",
                                 "KISSING FACE WITH SMILING EYES"), strings);
    }

    public void testBackSlashVariants() {
        List<String> nameList =
          new ArrayList<>(Arrays.asList("d", "D", "s", "S", "w", "W", "b", "B", "A", "G", "Z", "z", "Q", "E",
                                        "t", "n", "r", "f", "a", "e", "h", "H", "v", "V", "R", "X", "b{g}"));
        for (String[] stringArray : DefaultRegExpPropertiesProvider.getInstance().getAllKnownProperties()) {
            nameList.add("p{" + stringArray[0] + "}");
        }
        myFixture.testCompletionVariants(getInputDataFileName(getTestName(true)), ArrayUtil.toStringArray(nameList));
    }

    public void testPropertyVariants() {
        List<String> nameList = new ArrayList<>();
        for (String[] stringArray : DefaultRegExpPropertiesProvider.getInstance().getAllKnownProperties()) {
            nameList.add("{" + stringArray[0] + "}");
        }
        myFixture.testCompletionVariants(getInputDataFileName(getTestName(true)), ArrayUtil.toStringArray(nameList));
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
