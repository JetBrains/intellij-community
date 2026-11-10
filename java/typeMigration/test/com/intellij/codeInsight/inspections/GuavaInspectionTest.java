// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inspections;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.actions.CleanupInspectionIntention;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.typeMigration.TypeMigrationBundle;
import com.intellij.refactoring.typeMigration.inspections.GuavaInspection;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Dmitry Batkovich
 */
@TestApplication
public class GuavaInspectionTest {
  private static final LightProjectDescriptor DESCRIPTOR =
    new DefaultLightProjectDescriptor(IdeaTestUtil::getMockJdk18, List.of("com.google.guava:guava:20.0"));
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);
  
  private GuavaInspection myInspection;

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    codeInsightFixture.get().setTestDataPath(PlatformTestUtil.getCommunityPath() + "/java/typeMigration/testData/inspections/guava");
    myInspection = new GuavaInspection();
    myFixture.enableInspections(myInspection);
  }

  @Test
  public void testOptional() {
    doTest();
  }

  @Test
  public void testOptional2() {
    doTest();
  }

  @Test
  public void testOptional3() {
    doTest();
  }
  
  @Test
  public void testOptional4() {
    doTest();
  }

  @Test
  public void testOptionalArray() {
    doTestAllFile();
  }

  @Test
  public void testSimpleFluentIterable() {
    doTest();
  }

  @Test
  public void testChainedFluentIterable() {
    doTest();
  }

  @Test
  public void testFluentIterableChainWithoutVariable() {
    doTestAllFile();
  }

  @Test
  public void testChainedFluentIterableWithChainedInitializer() {
    doTest();
  }

  @Test
  public void testFluentIterableChainWithOptional() {
    doTest();
  }

  @Test
  public void testTransformAndConcat1() {
    doTest();
  }

  @Test
  public void testTransformAndConcat2() {
    doTest();
  }

  @Test
  public void testTransformAndConcat3() {
    doTest();
  }

  @Test
  public void testTransformAndConcat4() {
    doTest();
  }

  @Test
  public void testFilterIsInstance() {
    doTest();
  }

  @Test
  public void testInsertTypeParameter() {
    doTest();
  }

  @Test
  public void testRemoveMethodReferenceForFunctionalInterfaces() {
    doTest();
  }

  @Test
  public void testChainedFluentIterableWithOf() {
    doTest();
  }

  //needs Guava 18.0 as dependency
  public void _testAppend() {
    doTest();
  }

  @Test
  public void testChainContainsStopMethods() {
    doTestNoQuickFixes(PsiMethodCallExpression.class);
  }

  @Test
  public void testFluentIterableAndOptionalChain() {
    doTest();
  }

  @Test
  public void testCopyInto() {
    doTestAllFile();
  }

  @Test
  public void testToArray() {
    doTest();
  }

  @Test
  public void testToArray2() {
    doTest();
  }

  @Test
  public void testToArray3() {
    doTest();
  }

  @Test
  public void testReturnType() {
    doTest();
  }

  @Test
  public void testFluentIterableGet() {
    doTest();
  }

  @Test
  public void testFluentIterableGet2() {
    doTest();
  }

  @Test
  public void testIterableAssignment() {
    doTest();
  }

  @Test
  public void testReturnIterable() {
    doTest();
  }

  @Test
  public void testConvertFluentIterableAsIterableParameter() {
    doTest();
  }

  @Test
  public void testConvertFluentIterableAsIterableParameter2() {
    doTest();
  }

  @Test
  public void testConvertFunctionAsParameter() {
    doTest();
  }

  @Test
  public void testFluentIterableMigrationInInheritance() {
    doTest();
  }

  @Test
  public void testFluentIterableAndOptional() {
    doTest();
  }

  @Test
  public void testFluentIterableContains() {
    doTest();
  }

  @Test
  public void testFluentIterableChainSeparatedByMethods() {
    doTest();
  }

  @Test
  public void testFluentIterableWithStaticallyImportedFrom() {
    doTest();
  }

  @Test
  public void testTypeMigrationRootBackTraverse() {
    doTest();
  }

  @Test
  public void testOptionalTransform() {
    doTest();
  }

  @Test
  public void testOptionalTransform2() {
    doTest();
  }

  @Test
  public void testRemoveMethodReference() {
    doTest();
  }

  @Test
  public void testSimplifyOptionalComposition() {
    doTest();
  }

  @Test
  public void testMigrateArrays() {
    doTest();
  }

  @Test
  public void testConvertImmutableCollections() {
    doTestAllFile();
  }

  @Test
  public void testUniqueIndex() {
    doTestAllFile();
  }

  @Test
  public void testMigrateMethodAsChainQualifier() {
    doTest();
  }

  @Test
  public void testFixAllProblems() {
    doTestAllFile();
  }

  @Test
  public void testFixAllProblems2() {
    doTestAllFile();
  }

  @Test
  public void testPredicates() {
    doTestAllFile();
  }

  @Test
  public void testPredicates2() {
    doTestAllFile();
  }

  @Test
  public void testPredicates3() {
    doTestAllFile();
  }

  @Test
  public void testPredicates4() {
    doTestAllFile();
  }

  @Test
  public void testPredicates5() {
    doTest();
  }

  @Test
  public void testPredicates6() { doTestAllFile(); }

  @Test
  public void testPredicatesVarArg1() {
 doTestAllFile(); 
  }

  @Test
  public void testPredicatesVarArg2() {
 doTestAllFile(); 
  }

  @Test
  public void testPredicatesVarArg3() {
 doTestAllFile(); 
  }

  @Test
  public void testFluentIterableElementTypeChanged() {
    doTest();
  }

  // for ex: javax.annotations.Nullable is runtime annotation
  @Test
  public void testFunctionAnnotatedWithRuntimeAnnotation() {
    doTestAllFile();
  }

  @Test
  public void testFunctionAnnotatedWithRuntimeAnnotation2() {
    try {
      myInspection.ignoreJavaxNullable = false;
      doTestAllFile();
    } finally {
      myInspection.ignoreJavaxNullable = true;
    }
  }

  @Test
  public void testFluentIterableFromAndParenthesises() {
    doTestAllFile();
  }

  @Test
  public void testFunctionIsMethodReference() {
    doTest();
  }

  @Test
  public void testFluentIterableLast() {
    doTest();
  }

  @Test
  public void testLambdaImplementsBothInterfaces() {
    doTest();
  }

  private void doTestNoQuickFixes(@NotNull Class<? extends PsiElement> @NotNull ... highlightedElements) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(true) + ".java");

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
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(true) + ".java");
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
    assertTrue(actionFound, "Quick fix isn't found");
    myFixture.checkResultByFile(myFixture.getTestName(true) + "_after.java");
  }

  private void doTestAllFile() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(true) + ".java");
    myFixture.enableInspections(new GuavaInspection());
    List<HighlightInfo> infos = myFixture.doHighlighting();
    EdtTestUtil.runInEdtAndWait(() -> {
      for (HighlightInfo info : infos)
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
      myFixture.checkResultByFile(myFixture.getTestName(true) + "_after.java");
    });
  }
}
