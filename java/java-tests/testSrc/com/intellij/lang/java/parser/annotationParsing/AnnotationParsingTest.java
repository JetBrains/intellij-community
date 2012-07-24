/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.lang.java.parser.annotationParsing;

import com.intellij.lang.java.parser.JavaParsingTestCase;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiRecursiveElementVisitor;

/**
 * @author ven
 */
public class AnnotationParsingTest extends JavaParsingTestCase {
  public AnnotationParsingTest() {
    super("parser-full/annotationParsing/annotation");
  }

  public void testMarker() { doTest(true); }
  public void testSimple1() { doTest(true); }
  public void testSimple2() { doTest(true); }
  public void testComplex() { doTest(true); }
  public void testMultiple() { doTest(true); }
  public void testArray() { doTest(true); }
  public void testNested() { doTest(true); }
  public void testParameterAnnotation () { doTest(true); }
  public void testPackageAnnotation () { doTest(true); }
  public void testParameterizedMethod () { doTest(true); }
  public void testQualifiedAnnotation() { doTest(true); }
  public void testEnumSmartTypeCompletion() { doTest(true); }

  public void testTypeAnno() {
    withLevel(LanguageLevel.JDK_1_8, new Runnable() { @Override public void run() {
      doTest(true);
      myFile.accept(new PsiRecursiveElementVisitor() {
        @Override
        public void visitErrorElement(PsiErrorElement element) {
          fail(element.getErrorDescription());
          super.visitErrorElement(element);
        }
      });
    }});
  }

  public void testErrors() { doTest(true); }
}
