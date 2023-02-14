// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inspections;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.actions.CleanupInspectionIntention;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.typeMigration.TypeMigrationBundle;
import com.intellij.refactoring.typeMigration.inspections.GuavaInspection;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * @author Dmitry Batkovich
 */
public class GuavaInspectionTest extends JavaCodeInsightFixtureTestCase {
  private GuavaInspection myInspection;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myInspection = new GuavaInspection();
    myFixture.enableInspections(myInspection);
  }

  @Override
  protected String getTestDataPath()  {
    return PlatformTestUtil.getCommunityPath() + "/java/typeMigration/testData/inspections/guava";
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    moduleBuilder.setLanguageLevel(LanguageLevel.JDK_1_8);
    moduleBuilder.addLibraryJars("guava", getTestDataPath() + "/", "guava-stubs.jar");
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
  
  public void testOptional4() {
    doTest();
  }

  public void testOptionalArray() {
    doTestAllFile();
  }

  public void testSimpleFluentIterable() {
    doTest();
  }

  public void testChainedFluentIterable() {
    doTest();
  }

  public void testFluentIterableChainWithoutVariable() {
    doTestAllFile();
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

  public void testRemoveMethodReferenceForFunctionalInterfaces() {
    doTest();
  }

  public void testChainedFluentIterableWithOf() {
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
    doTestAllFile();
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

  public void testConvertFluentIterableAsIterableParameter2() {
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

  public void testFixAllProblems2() {
    doTestAllFile();
  }

  public void testPredicates() {
    doTestAllFile();

  }

  public void testPredicates2() {
    doTestAllFile();
  }

  public void testPredicates3() {
    doTestAllFile();
  }

  public void testPredicates4() {
    doTestAllFile();
  }

  public void testPredicates5() {
    doTest();
  }

  public void testFluentIterableElementTypeChanged() {
    doTest();
  }

  // for ex: javax.annotations.Nullable is runtime annotation
  public void testFunctionAnnotatedWithRuntimeAnnotation() {
    doTestAllFile();
  }

  public void testFunctionAnnotatedWithRuntimeAnnotation2() {
    try {
      myInspection.ignoreJavaxNullable = false;
      doTestAllFile();
    } finally {
      myInspection.ignoreJavaxNullable = true;
    }
  }

  public void testFluentIterableFromAndParenthesises() {
    doTestAllFile();
  }

  public void testFunctionIsMethodReference() {
    doTest();
  }

  public void testFluentIterableLast() {
    doTest();
  }

  public void testLambdaImplementsBothInterfaces() {
    doTest();
  }

  private void doTestNoQuickFixes(@NotNull Class<? extends PsiElement> @NotNull ... highlightedElements) {
    myFixture.configureByFile(getTestName(true) + ".java");

    myFixture.doHighlighting();
    for (IntentionAction action : myFixture.getAvailableIntentions()) {
      if (TypeMigrationBundle.message("migrate.guava.to.java.family.name").equals(action.getFamilyName())) {
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
      if (TypeMigrationBundle.message("migrate.guava.to.java.family.name").equals(action.getFamilyName())) {
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
    for (HighlightInfo info : myFixture.doHighlighting())
      if (TypeMigrationBundle.message("guava.functional.primitives.can.be.replaced.by.java.api.problem.description").equals(info.getDescription())) {
        HighlightInfo.IntentionActionDescriptor desc = info.findRegisteredQuickFix((descriptor, range) -> descriptor);
        final PsiElement someElement = myFixture.getFile().findElementAt(0);
        assertNotNull(someElement);
        boolean doBreak = false;
        for (IntentionAction option : desc.getOptions(someElement, myFixture.getEditor())) {
          if (option instanceof CleanupInspectionIntention) {
            myFixture.launchAction(option);
            doBreak = true;
            break;
          }
        }
        if (doBreak) {
          break;
        }
      }
    myFixture.checkResultByFile(getTestName(true) + "_after.java");
  }
}
