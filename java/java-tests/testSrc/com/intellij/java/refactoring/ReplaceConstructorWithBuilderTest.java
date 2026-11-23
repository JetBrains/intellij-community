// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.LightMultiFileFixture;
import com.intellij.refactoring.replaceConstructorWithBuilder.ParameterData;
import com.intellij.refactoring.replaceConstructorWithBuilder.ReplaceConstructorWithBuilderProcessor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.intellij.refactoring.LightMultiFileFixtureKt.lightMultiFileFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class ReplaceConstructorWithBuilderTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightMultiFileFixture> multiFile =
    lightMultiFileFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/refactoring/replaceConstructorWithBuilder/");

  @Test
  public void testVarargs() {
    doTest(true);
  }

  @Test
  public void testExistingEmptyBuilder() {
    doTest(false);
  }

  @Test
  public void testMultipleParams() {
    doTest(true);
  }

  @Test
  public void testExistingHalfEmptyBuilder() {
    doTest(false);
  }

  @Test
  public void testExistingVoidSettersBuilder() {
    doTest(false);
  }

  @Test
  public void testConstructorChain() {
    final HashMap<String, String> defaults = new HashMap<>();
    defaults.put("i", "2");
    doTest(true, defaults);
  }

  @Test
  public void testConstructorChainWithoutDefaults() {
    final HashMap<String, String> defaults = new HashMap<>();
    defaults.put("i", "2");
    defaults.put("j", null);
    doTest(true, defaults);
  }

  @Test
  public void testConstructorTree() {
    doTest(true, null, "Constructors of class <b><code>Test</code></b> do not form a simple chain.");
  }

  @Test
  public void testGenerics() {
    doTest(true);
  }
  
  @Test
  public void testGenericsImport() {
    doTest(true);
  }

  @Test
  public void testImports() {
    doTest(true, null, null, "foo");
  }
  
  @Test
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
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    multiFile.get().doTest(() -> {
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
          new ReplaceConstructorWithBuilderProcessor(myFixture.getProject(), constructors, map, "Builder", packageName, null, createNew, false);
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
