// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.LightMultiFileTestCase;
import com.intellij.refactoring.replaceConstructorWithBuilder.ParameterData;
import com.intellij.refactoring.replaceConstructorWithBuilder.ReplaceConstructorWithBuilderProcessor;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class ReplaceConstructorWithBuilderTest extends LightMultiFileTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/refactoring/replaceConstructorWithBuilder/";
  }

  public void testVarargs() {
    doTest(true);
  }

  public void testExistingEmptyBuilder() {
    doTest(false);
  }

  public void testMultipleParams() {
    doTest(true);
  }

  public void testExistingHalfEmptyBuilder() {
    doTest(false);
  }

  public void testExistingVoidSettersBuilder() {
    doTest(false);
  }

  public void testConstructorChain() {
    final HashMap<String, String> defaults = new HashMap<>();
    defaults.put("i", "2");
    doTest(true, defaults);
  }

  public void testConstructorChainWithoutDefaults() {
    final HashMap<String, String> defaults = new HashMap<>();
    defaults.put("i", "2");
    defaults.put("j", null);
    doTest(true, defaults);
  }

  public void testConstructorTree() {
    doTest(true, null, "Constructors of class <b><code>Test</code></b> do not form a simple chain.");
  }

  public void testGenerics() {
    doTest(true);
  }
  
  public void testGenericsImport() {
    doTest(true);
  }

  public void testImports() {
    doTest(true, null, null, "foo");
  }
  
  public void testAnonymousInheritor() {
    doTest(true);
  }

  private void doTest(boolean createNewBuilderClass) {
    doTest(createNewBuilderClass, null);
  }

  private void doTest(boolean createNewBuilderClass, Map<String, String> expectedDefaults) {
    doTest(createNewBuilderClass, expectedDefaults, null);
  }

  private void doTest(boolean createNewBuilderClass, Map<String, String> expectedDefaults, String conflicts) {
    doTest(createNewBuilderClass, expectedDefaults, conflicts, "");
  }

  private void doTest(boolean createNew, Map<String, String> expectedDefaults, String conflicts, String packageName) {
    doTest(() -> {
      final PsiClass aClass = myFixture.findClass("Test");

      final LinkedHashMap<String, ParameterData> map = new LinkedHashMap<>();
      final PsiMethod[] constructors = aClass.getConstructors();
      for (PsiMethod constructor : constructors) {
        ParameterData.createFromConstructor(constructor, "set", map);
      }
      if (expectedDefaults != null) {
        for (Map.Entry<String, String> entry : expectedDefaults.entrySet()) {
          final ParameterData parameterData = map.get(entry.getKey());
          assertNotNull(parameterData);
          assertEquals(entry.getValue(), parameterData.getDefaultValue());
        }
      }
      try {
        final ReplaceConstructorWithBuilderProcessor processor =
          new ReplaceConstructorWithBuilderProcessor(getProject(), constructors, map, "Builder", packageName, null, createNew, false);
        processor.run();
        if (conflicts != null) {
          fail("Conflicts were not detected: " + conflicts);
        }
      }
      catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
        if (conflicts == null) {
          fail("Conflict detected: " + e.getMessage());
        }
        else if (!conflicts.equals(e.getMessage())) {
          fail("Conflicts do not match. Expected:\n" + conflicts + "\nActual:\n" + e.getMessage());
        }
      }
    });
  }
}
