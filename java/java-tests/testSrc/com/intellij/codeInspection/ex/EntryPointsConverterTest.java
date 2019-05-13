// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.ex.EntryPointsManagerBase;
import com.intellij.codeInspection.reference.SmartRefElementPointer;
import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.openapi.util.JDOMUtil;
import java.util.HashMap;
import junit.framework.TestCase;
import org.jdom.Element;

public class EntryPointsConverterTest extends TestCase {
  public void testMethodConverter1() {
    doTest("method", "String java.lang.String.replace(char oldChar, char newChar)", "java.lang.String String replace(char oldChar, char newChar)");
  }

  public void testMethodConverter2() {
    doTest("method", "void java.lang.String.replace(char oldChar, char newChar)", "java.lang.String void replace(char oldChar, char newChar)");
  }

  public void testMethodConverter3() {
    doTest("method", "java.lang.String.String(char oldChar)", "java.lang.String String(char oldChar)");
  }

  public void testFieldConverter() {
    doTest("field", "java.lang.String.myFakeField", "java.lang.String myFakeField");
  }

  private static void doTest(String type, String fqName, String expectedFQName) {
    final Element entryPoints = setUpEntryPoint(type, fqName);

    final HashMap<String, SmartRefElementPointer> persistentEntryPoints = new HashMap<>();
    EntryPointsManagerBase.convert(entryPoints, persistentEntryPoints);

    final Element testElement = new Element("comp");
    EntryPointsManagerBase.writeExternal(testElement, persistentEntryPoints, new JDOMExternalizableStringList());

    final Element expectedEntryPoints = setUpEntryPoint(type, expectedFQName);
    expectedEntryPoints.setAttribute("version", "2.0");
    final Element expected = new Element("comp");
    expected.addContent(expectedEntryPoints);

    assertTrue(JDOMUtil.areElementsEqual(testElement, expected));
  }

  private static Element setUpEntryPoint(String type, String fqName) {
    Element entryPoints = new Element("entry_points");
    Element entryPoint = new Element("entry_point");
    entryPoint.setAttribute("TYPE", type);
    entryPoint.setAttribute("FQNAME", fqName);
    entryPoints.addContent(entryPoint);
    return entryPoints;
  }

}
