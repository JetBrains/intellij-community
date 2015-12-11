/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight.inspections;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.quickfix.VariableTypeFix;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.PathManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.typeMigration.inspections.GuavaInspection;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

import java.io.File;
import java.util.Arrays;

/**
 * @author Dmitry Batkovich
 */
public class GuavaInspectionTest extends JavaCodeInsightFixtureTestCase {

  @Override
  protected String getTestDataPath()  {
    return PlatformTestUtil.getCommunityPath() + "/java/typeMigration/testData/inspections/guava";
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) throws Exception {
    moduleBuilder.setLanguageLevel(LanguageLevel.JDK_1_8);
    moduleBuilder.addLibraryJars("guava-17.0.jar", PathManager.getHomePath().replace(File.separatorChar, '/') + "/community/lib/",
                                 "guava-17.0.jar");
    moduleBuilder.addLibraryJars("guava-17.0.jar-2", PathManager.getHomePath().replace(File.separatorChar, '/') + "/lib/",
                                 "guava-17.0.jar");
    moduleBuilder.addJdk(IdeaTestUtil.getMockJdk18Path().getPath());
  }

  public void testOptional() {
    doTest();
  }

  public void testOptional2() {
    doTest();
  }

  public void testOptional3() {
    doTest();
  }

  public void testSimpleFluentIterable() {
    doTest();
  }

  public void testChainedFluentIterable() {
    doTest();
  }

  public void testFluentIterableChainWithoutVariable() {
    doTest();
  }

  public void testChainedFluentIterableWithChainedInitializer() {
    doTest();
  }

  public void testFluentIterableChainWithOptional() {
    doTest();
  }

  public void testTransformAndConcat1() {
    doTest();
  }

  public void testTransformAndConcat2() {
    doTest();
  }

  public void testTransformAndConcat3() {
    doTest();
  }

  public void testTransformAndConcat4() {
    doTest();
  }

  public void testFilterIsInstance() {
    doTest();
  }

  public void testInsertTypeParameter() {
    doTest();
  }

  public void testDontShowFluentIterableChainQuickFix() {
    doTestNoQuickFixes(PsiMethodCallExpression.class);
  }

  public void testRemoveMethodReferenceForFunctionalInterfaces() {
    doTest();
  }

  //needs Guava 18.0 as dependency
  public void _testChainedFluentIterableWithOf() {
    doTest();
  }

  //needs Guava 18.0 as dependency
  public void _testAppend() {
    doTest();
  }

  public void testChainContainsStopMethods() {
    doTestNoQuickFixes(PsiMethodCallExpression.class);
  }

  public void testFluentIterableAndOptionalChain() {
    doTest();
  }

  public void testCopyInto() {
    doTest();
  }

  public void testToArray() {
    doTest();
  }

  public void testToArray2() {
    doTest();
  }

  public void testToArray3() {
    doTest();
  }

  public void testReturnType() {
    doTest();
  }

  public void testFluentIterableGet() {
    doTest();
  }

  public void testFluentIterableGet2() {
    doTest();
  }

  public void testIterableAssignment() {
    doTest();
  }

  public void testReturnIterable() {
    doTest();
  }

  public void testConvertFluentIterableAsIterableParameter() {
    doTest();
  }

  public void testConvertFunctionAsParameter() {
    doTest();
  }

  public void testFluentIterableMigrationInInheritance() {
    doTest();
  }

  public void testFluentIterableAndOptional() {
    doTest();
  }

  public void testFluentIterableContains() {
    doTest();
  }

  public void testFluentIterableChainSeparatedByMethods() {
    doTest();
  }

  public void testFluentIterableWithStaticallyImportedFrom() {
    doTest();
  }

  public void testTypeMigrationRootBackTraverse() {
    doTest();
  }

  public void testOptionalTransform() {
    doTest();
  }

  public void testOptionalTransform2() {
    doTest();
  }

  public void testRemoveMethodReference() {
    doTest();
  }

  public void testSimplifyOptionalComposition() {
    doTest();
  }

  public void testMigrateArrays() {
    doTest();
  }

  public void testConvertImmutableCollections() {
    doTestAllFile();
  }

  public void testUniqueIndex() {
    doTestAllFile();
  }

  public void testMigrateMethodAsChainQualifier() {
    doTest();
  }

  public void testFixAllProblems() {
    doTestAllFile();
  }

  private void doTestNoQuickFixes(Class<? extends PsiElement>... highlightedElements) {
    myFixture.configureByFile(getTestName(true) + ".java");
    myFixture.enableInspections(new GuavaInspection());
    myFixture.doHighlighting();
    for (IntentionAction action : myFixture.getAvailableIntentions()) {
      if (action instanceof GuavaInspection.MigrateGuavaTypeFix) {
        final PsiElement element = ((GuavaInspection.MigrateGuavaTypeFix)action).getStartElement();
        if (PsiTreeUtil.instanceOf(element, highlightedElements)) {
          fail("Quick fix is found but not expected for types " + Arrays.toString(highlightedElements));
        }
      }
    }
  }

  private void doTest() {
    myFixture.configureByFile(getTestName(true) + ".java");
    myFixture.enableInspections(new GuavaInspection());
    boolean actionFound = false;
    myFixture.doHighlighting();
    for (IntentionAction action : myFixture.getAvailableIntentions()) {
      if (action instanceof GuavaInspection.MigrateGuavaTypeFix) {
        myFixture.launchAction(action);
        actionFound = true;
        break;
      }
    }
    assertTrue("Quick fix isn't found", actionFound);
    myFixture.checkResultByFile(getTestName(true) + "_after.java");
  }

  private void doTestAllFile() {
    myFixture.configureByFile(getTestName(true) + ".java");
    myFixture.enableInspections(new GuavaInspection());
    for (HighlightInfo info : myFixture.doHighlighting()) {
      if (GuavaInspection.PROBLEM_DESCRIPTION_FOR_METHOD_CHAIN.equals(info.getDescription()) ||
          GuavaInspection.PROBLEM_DESCRIPTION_FOR_VARIABLE.equals(info.getDescription())) {
        myFixture.launchAction(info.quickFixActionMarkers.get(0).getFirst().getAction());
      }
    }
    myFixture.checkResultByFile(getTestName(true) + "_after.java");
  }
}
