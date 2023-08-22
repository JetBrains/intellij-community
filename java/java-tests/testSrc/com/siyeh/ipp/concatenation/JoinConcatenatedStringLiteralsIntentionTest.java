// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.concatenation;

import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;

public class JoinConcatenatedStringLiteralsIntentionTest extends IPPTestCase {
    public void testSimple() { doTest(); }
    public void testPolyadic() { doTest(); }
    public void testNonString() { doTest(); }
    public void testNonString2() { doTest(); }
    public void testNotAvailable() { assertIntentionNotAvailable(); }
    public void testInvalidLiteral() { assertIntentionNotAvailable(); }
    public void testKeepCommentsAndWhitespace() { doTest(); }
    public void testTextBlocks() { doTest(); }
    public void testTextBlocksTailingLineBreak() { doTest(); }
    public void testTextBlocksAndStringLiteral() { doTest(); }
    public void testKeepEscapes() { doTest(); }


    @Override
    protected String getIntentionName() {
        return IntentionPowerPackBundle.message(
                "join.concatenated.string.literals.intention.name");
    }

    @Override
    protected String getRelativePath() {
        return "concatenation/join_concat";
    }
}
