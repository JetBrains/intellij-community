// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser.partial;

import com.intellij.java.parser.AbstractBasicJavaParsingTestCase;
import com.intellij.java.parser.AbstractBasicJavaParsingTestConfigurator;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractBasicAnnotationParserTest extends AbstractBasicJavaParsingTestCase {
  public AbstractBasicAnnotationParserTest(@NotNull AbstractBasicJavaParsingTestConfigurator configurator) {
    super("parser-partial/annotations", configurator);
  }

  public void testMarker() { doParserTest("@Preliminary"); }

  public void testSimple0() { doParserTest("@Copyright(\"blah-blah-blah\")"); }

  public void testSimple1() { doParserTest("@Copyright(treatedAsValue)"); }

  public void testComplex() { doParserTest("@Author(first=\"Eugene\", second=\"Another Eugene\")"); }

  public void testMultiple() { doParserTest("@Preliminary @Other(name=value)"); }

  public void testArray() { doParserTest("@Endorsers({\"Children\", \"Unscrupulous dentists\"})"); }

  public void testNested() { doParserTest("@Author(@Name(first=\"Eugene\", second=\"Yet One Eugene\"))"); }

  public void testQualifiedAnnotation() { doParserTest("@org.jetbrains.annotations.Nullable"); }

  public void testExtraCommaInList() { doParserTest("@Anno({0, 1,})"); }

  public void testParameterizedAnnotation() { doParserTest("@Nullable<T>"); }

  public void testFirstNameMissed() { doParserTest("@Anno(value1, param2=value2)"); }

  protected abstract void doParserTest(String text);
}