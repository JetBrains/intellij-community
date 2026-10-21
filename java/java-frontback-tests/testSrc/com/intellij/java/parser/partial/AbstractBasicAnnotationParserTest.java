// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser.partial;

import com.intellij.java.parser.AbstractBasicJavaParsingTestCase;
import com.intellij.java.parser.AbstractBasicJavaParsingTestConfigurator;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

public abstract class AbstractBasicAnnotationParserTest extends AbstractBasicJavaParsingTestCase {
  public AbstractBasicAnnotationParserTest(@NotNull AbstractBasicJavaParsingTestConfigurator configurator) {
    super("parser-partial/annotations", configurator);
  }

  @Test
  public void testMarker() { doParserTest("@Preliminary"); }

  @Test
  public void testSimple0() { doParserTest("@Copyright(\"blah-blah-blah\")"); }

  @Test
  public void testSimple1() { doParserTest("@Copyright(treatedAsValue)"); }

  @Test
  public void testComplex() { doParserTest("@Author(first=\"Eugene\", second=\"Another Eugene\")"); }

  @Test
  public void testMultiple() { doParserTest("@Preliminary @Other(name=value)"); }

  @Test
  public void testArray() { doParserTest("@Endorsers({\"Children\", \"Unscrupulous dentists\"})"); }

  @Test
  public void testNested() { doParserTest("@Author(@Name(first=\"Eugene\", second=\"Yet One Eugene\"))"); }

  @Test
  public void testQualifiedAnnotation() { doParserTest("@org.jetbrains.annotations.Nullable"); }

  @Test
  public void testExtraCommaInList() { doParserTest("@Anno({0, 1,})"); }

  @Test
  public void testParameterizedAnnotation() { doParserTest("@Nullable<T>"); }

  @Test
  public void testFirstNameMissed() { doParserTest("@Anno(value1, param2=value2)"); }

  protected abstract void doParserTest(String text);
}