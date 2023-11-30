// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.performance;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.performance.ArraysAsListWithZeroOrOneArgumentInspection;

/**
 * @author Bas Leijdekkers
 */
@SuppressWarnings({"ClassInitializerMayBeStatic", "ArraysAsListWithZeroOrOneArgument"})
public class ArraysAsListWithZeroOrOneArgumentFixJava9Test extends IGQuickFixesTestCase {

  public void testZeroArguments() {
    doTest(CommonQuickFixBundle.message("fix.replace.with.x", "List.of()"),
           """
             import java.util.*;
             class X {{
                 Object o = Arrays.asList/**/();
             }}""",
           """
             import java.util.*;
             class X {{
                 Object o = List.of();
             }}""");
  }

  @SuppressWarnings("RedundantOperationOnEmptyContainer")
  public void testZeroArgumentsWithType() {
    doTest(CommonQuickFixBundle.message("fix.replace.with.x", "List.of()"),
           """
             import java.util.*;
             class X {{
                 Spliterator<String> it = Arrays.<String>/**/asList().spliterator();
             }}""",
           """
             import java.util.*;
             class X {{
                 Spliterator<String> it = List.<String>of().spliterator();
             }}""");
  }

  public void testOneArgument() {
    doTest(CommonQuickFixBundle.message("fix.replace.with.x", "List.of()"),
           "import java.util.*;" +
           "class X {{\n" +
           "  List<Map<String, String>> list = Arrays./**/asList(new HashMap<>());" +
           "}}",
           "import java.util.*;" +
           "class X {{\n" +
           "  List<Map<String, String>> list = List.of(new HashMap<>());" +
           "}}");
  }

  public void testOneArgumentNullable() {
    doTest(CommonQuickFixBundle.message("fix.replace.with.x", "Collections.singletonList()"),
           "import java.util.*;" +
           "class X {{\n" +
           "  List<?> list = Arrays./**/asList((String)null);" +
           "}}",
           "import java.util.*;" +
           "class X {{\n" +
           "  List<?> list = Collections.singletonList((String) null);" +
           "}}");
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder builder) throws Exception {
    builder.setLanguageLevel(LanguageLevel.JDK_1_9);
  }

  @Override
  protected BaseInspection getInspection() {
    return new ArraysAsListWithZeroOrOneArgumentInspection();
  }
}
