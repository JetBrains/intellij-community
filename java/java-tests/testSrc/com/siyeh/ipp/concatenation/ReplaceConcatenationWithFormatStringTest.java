// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.concatenation;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;
import org.jetbrains.annotations.NotNull;

public class ReplaceConcatenationWithFormatStringTest extends IPPTestCase {
    public void testNumericBinaryExpression() { doTest(); }
    public void testHexadecimalLiteral() { doTest(); }
    public void testPercentInLiteral() { doTest(); }
    public void testParameters() { doTest(); }
    public void testLineSeparator() { doTest(); }
    public void testPreserveTextBlock() {
        IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_15, () -> {
            doTest(IntentionPowerPackBundle.message(
              "replace.concatenation.with.format.string.intention.name.formatted"));
        });
    }

    @Override
    protected String getIntentionName() {
        return IntentionPowerPackBundle.message(
                "replace.concatenation.with.format.string.intention.name");
    }

    @Override
    protected String getRelativePath() {
        return "concatenation/string_format";
    }

    @NotNull
    @Override
    protected LightProjectDescriptor getProjectDescriptor() {
        return JAVA_11;
    }
}
