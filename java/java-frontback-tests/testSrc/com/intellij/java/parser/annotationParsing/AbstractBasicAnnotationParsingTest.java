// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser.annotationParsing;

import com.intellij.java.parser.AbstractBasicJavaParsingTestCase;
import com.intellij.java.parser.AbstractBasicJavaParsingTestConfigurator;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractBasicAnnotationParsingTest extends AbstractBasicJavaParsingTestCase {
  public AbstractBasicAnnotationParsingTest(@NotNull AbstractBasicJavaParsingTestConfigurator configurator) {
    super("parser-full/annotationParsing/annotation", configurator);
  }

  public void testMarker() { doTest(true); }

  public void testSimple1() { doTest(true); }

  public void testSimple2() { doTest(true); }

  public void testComplex() { doTest(true); }

  public void testMultiple() { doTest(true); }

  public void testArray() { doTest(true); }

  public void testNested() { doTest(true); }

  public void testParameterAnnotation() { doTest(true); }

  public void testPackageAnnotation() { doTest(true); }

  public void testParameterizedMethod() { doTest(true); }

  public void testQualifiedAnnotation() { doTest(true); }

  public void testEnumSmartTypeCompletion() { doTest(true); }

  public void testTypeAnnotations() { doTest(true); }
}
