// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser.annotationParsing;

import com.intellij.java.parser.AbstractBasicJavaParsingTestCase;
import com.intellij.java.parser.AbstractBasicJavaParsingTestConfigurator;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

public abstract class AbstractBasicAnnotationParsingTest extends AbstractBasicJavaParsingTestCase {
  public AbstractBasicAnnotationParsingTest(@NotNull AbstractBasicJavaParsingTestConfigurator configurator) {
    super("parser-full/annotationParsing/annotation", configurator);
  }

  @Test
  public void testMarker() { doTest(true); }

  @Test
  public void testSimple1() { doTest(true); }

  @Test
  public void testSimple2() { doTest(true); }

  @Test
  public void testComplex() { doTest(true); }

  @Test
  public void testMultiple() { doTest(true); }

  @Test
  public void testArray() { doTest(true); }

  @Test
  public void testNested() { doTest(true); }

  @Test
  public void testParameterAnnotation() { doTest(true); }

  @Test
  public void testPackageAnnotation() { doTest(true); }

  @Test
  public void testParameterizedMethod() { doTest(true); }

  @Test
  public void testQualifiedAnnotation() { doTest(true); }

  @Test
  public void testEnumSmartTypeCompletion() { doTest(true); }

  @Test
  public void testTypeAnnotations() { doTest(true); }
}
