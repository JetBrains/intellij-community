// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.performance;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.performance.ArraysAsListWithZeroOrOneArgumentInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class ArraysAsListWithZeroOrOneArgumentFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new ArraysAsListWithZeroOrOneArgumentInspection()));

  @Test
  public void testZeroArguments() {
    ig.get().doTest(CommonQuickFixBundle.message("fix.replace.with.x", "Collections.emptyList()"),
           """
             import java.util.*;
             class X {{
                 Arrays.asList/**/();
             }}""",
           """
             import java.util.*;
             class X {{
                 Collections.emptyList();
             }}""");
  }

  @Test
  @SuppressWarnings("RedundantOperationOnEmptyContainer")
  public void testZeroArgumentsWithType() {
    ig.get().doTest(CommonQuickFixBundle.message("fix.replace.with.x", "Collections.emptyList()"),
           """
             import java.util.*;
             class X {{
                 Spliterator<String> it = Arrays.<String>/**/asList().spliterator();
             }}""",
           """
             import java.util.*;
             class X {{
                 Spliterator<String> it = Collections.<String>emptyList().spliterator();
             }}""");
  }

  @Test
  public void testOneArgument() {
    ig.get().doTest(CommonQuickFixBundle.message("fix.replace.with.x", "Collections.singletonList()"),
           "import java.util.*;" +
           "class X {{\n" +
           "  List<Map<String, String>> list = Arrays./**/asList(new HashMap<>());" +
           "}}",
           "import java.util.*;" +
           "class X {{\n" +
           "  List<Map<String, String>> list = Collections.singletonList(new HashMap<>());" +
           "}}");
  }

}
