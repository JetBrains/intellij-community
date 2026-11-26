// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.multiverse.EditorContextManager;
import com.intellij.java.refactoring.ChangeSignatureFixture.GenParams;
import com.intellij.java.refactoring.ChangeSignatureFixture.SimpleParameterGen;
import com.intellij.java.refactoring.ChangeSignatureFixture.SimpleExceptionsGen;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiCall;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEllipsisType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.util.JavaPsiRecordUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.JavaThrownExceptionInfo;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.refactoring.changeSignature.ThrownExceptionInfo;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ArrayUtil;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static com.intellij.java.refactoring.ChangeSignatureFixtureKt.changeSignatureFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class ChangeSignatureTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private static final String RELATIVE_PATH = "/refactoring/changeSignature/";
  private final TestFixture<ChangeSignatureFixture> changeSignature =
    changeSignatureFixture(projectFixture, RELATIVE_PATH, ChangeSignatureTest::getTargetElement);

  private CommonCodeStyleSettings getCommonSettings() {
    return CodeStyle.getSettings(changeSignature.get().getFixture().getProject()).getCommonSettings(JavaLanguage.INSTANCE);
  }

  private static @Nullable PsiElement getTargetElement(LightJavaCodeInsightTestFixture myFixture) {
    PsiElement target = ChangeSignatureFixture.findTargetElement(myFixture);
    if (target instanceof PsiClass aClass) {
      return JavaPsiRecordUtil.findCanonicalConstructor(aClass);
    }
    if (target instanceof PsiRecordComponent component) {
      return JavaPsiRecordUtil.findCanonicalConstructor(component.getContainingClass());
    }
    if (myFixture.getTestName(false).contains("Implicit")) {
      return findCalledElement(myFixture);
    }
    return target;
  }
  
  private static PsiElement findCalledElement(LightJavaCodeInsightTestFixture myFixture) {
    Editor editor = myFixture.getEditor();
    Project project = myFixture.getProject();
    PsiFile file = EditorContextManager.getPsiFileForEditor(editor, project);
    if (file == null) return null;
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    PsiCall call = PsiTreeUtil.getParentOfType(element, PsiCall.class);
    return call.resolveMethod();
  }

  @Test
  public void testSimple() {
    changeSignature.get().doTest(null, null, null, new ParameterInfoImpl[0], new ThrownExceptionInfo[0], false);
  }

  @Test
  public void testParameterReorder() {
    changeSignature.get().doTest(null, new ParameterInfoImpl[]{ParameterInfoImpl.create(1), ParameterInfoImpl.create(0)}, false);
  }

  @Test
  public void testWarnAboutContract() {
    assertConflict(() -> {
      changeSignature.get().doTest(null, new ParameterInfoImpl[]{ParameterInfoImpl.create(1)}, false);
    }, "@Contract annotation cannot be updated automatically: Parameter 'i' was deleted, but contract clause 'null, _ -> fail' depends on it");
  }

  @Test
  public void testWarnAboutAssigningWeakerAccessPrivileges() {
    assertConflict(() -> {
      changeSignature.get().doTest(PsiModifier.PRIVATE, null, null, new ParameterInfoImpl[0], new ThrownExceptionInfo[0], false);
    }, "method <b><code>f()</code></b> will have incompatible access privileges with super method <b><code>X.f()</code></b>");
  }

  @Test
  public void testDelegateWithoutChangesWarnAboutSameMethodInClass() {
    assertConflict(() -> {
      changeSignature.get().doTest(null, new ParameterInfoImpl[0], true);
    }, "Method <b><code>m()</code></b> is already defined in class <b><code>A</code></b>");
  }

  @Test
  public void testDuplicatedSignatureInInheritor() {
    assertConflict(() -> {
      changeSignature.get().doTest(null, new ParameterInfoImpl[]{ParameterInfoImpl.createNew().withName("i").withType(PsiTypes.intType())}, true);
    }, "Method <b><code>foo(int)</code></b> is already defined in class <b><code>B</code></b>");
  }

  @Test
  public void testConflictForUsedParametersInMethodBody() {
    assertConflict(() -> {
      changeSignature.get().doTest(null, new ParameterInfoImpl[0], true);
    }, "Parameter <b><code>i</code></b> is used in method body");
  }

  @Test
  public void testNoConflictForSuperCallDelegation() {
    changeSignature.get().doTest(null, new ParameterInfoImpl[0], false);
  }

  @Test
  public void testGenericTypes() {
    changeSignature.get().doTest(null, null, "T", method -> new ParameterInfoImpl[]{
      ParameterInfoImpl.createNew().withName("x").withType(changeSignature.get().getFactory().createTypeFromText("T", method.getParameterList())).withDefaultValue(
        "null"),
      ParameterInfoImpl.createNew().withName("y")
        .withType(changeSignature.get().getFactory().createTypeFromText("C<T>", method.getParameterList())).withDefaultValue("null")
    }, false);
  }

  @Test
  public void testGenericTypesInOldParameters() {
    changeSignature.get().doTest(null, null, null, method -> new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0).withName("t").withType(changeSignature.get().getFactory().createTypeFromText("T", method)).withDefaultValue(null)
    }, false);
  }

  @Test
  public void testTypeParametersInMethod() {
    changeSignature.get().doTest(null, null, null, method -> new ParameterInfoImpl[]{
      ParameterInfoImpl.createNew().withName("t").withType(changeSignature.get().getFactory().createTypeFromText("T", method.getParameterList())).withDefaultValue(
        "null"),
      ParameterInfoImpl.createNew().withName("u").withType(changeSignature.get().getFactory().createTypeFromText("U", method.getParameterList())).withDefaultValue(
        "null"),
      ParameterInfoImpl.createNew().withName("cu")
        .withType(changeSignature.get().getFactory().createTypeFromText("C<U>", method.getParameterList())).withDefaultValue("null")
    }, false);
  }

  @Test
  public void testDefaultConstructor() {
    changeSignature.get().doTest(null,
           new ParameterInfoImpl[]{
             ParameterInfoImpl.createNew().withName("j").withType(PsiTypes.intType()).withDefaultValue("27")
           }, false
    );
  }

  @Test
  public void testFlexibleConstructorBody() {
    changeSignature.get().doTest(null,
           new ParameterInfoImpl[]{
             ParameterInfoImpl.createNew().withName("i").withType(PsiTypes.intType()).withDefaultValue("0")
           }, false
    );
  }

  @Test
  public void testGenerateDelegate() {
    changeSignature.get().doTest(null,
           new ParameterInfoImpl[]{
             ParameterInfoImpl.createNew().withName("i").withType(PsiTypes.intType()).withDefaultValue("27")
           }, true
    );
  }

  @Test
  public void testGenerateDelegateForAbstract() {
    changeSignature.get().doTest(null,
           new ParameterInfoImpl[]{
             ParameterInfoImpl.createNew().withName("i").withType(PsiTypes.intType()).withDefaultValue("27")
           }, true
    );
  }

  @Test
  public void testGenerateDelegateWithReturn() {
    changeSignature.get().doTest(null,
           new ParameterInfoImpl[]{
             ParameterInfoImpl.createNew().withName("i").withType(PsiTypes.intType()).withDefaultValue("27")
           }, true
    );
  }

  @Test
  public void testGenerateDelegateWithParametersReordering() {
    changeSignature.get().doTest(null,
           new ParameterInfoImpl[]{
             ParameterInfoImpl.create(1),
             ParameterInfoImpl.createNew().withName("c").withType(PsiTypes.charType()).withDefaultValue("'a'"),
             ParameterInfoImpl.create(0).withName("j").withType(PsiTypes.intType())
           }, true
    );
  }

  @Test
  public void testGenerateDelegateConstructor() {
    changeSignature.get().doTest(null, new ParameterInfoImpl[0], true);
  }

  @Test
  public void testGenerateDelegateDefaultConstructor() {
    changeSignature.get().doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.createNew().withName("i").withType(PsiTypes.intType()).withDefaultValue("27")
    }, true);
  }

  @Test
  public void testSCR40895() {
    changeSignature.get().doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0).withName("y").withType(PsiTypes.intType()),
      ParameterInfoImpl.create(1).withName("b").withType(PsiTypes.booleanType())
    }, false);
  }

  @Test
  public void testJavadocGenericsLink() {
    changeSignature.get().doTest(null, ReadAction.computeBlocking(() -> new ParameterInfoImpl[]{
      ParameterInfoImpl.createNew().withName("y").withType(changeSignature.get().getFactory().createTypeFromText("java.util.List<java.lang.String>", null)),
      ParameterInfoImpl.create(0).withName("a").withType(PsiTypes.booleanType())
    }), false);
  }

  @Test
  public void testParamNameSameAsFieldName() {
    changeSignature.get().doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0).withName("fieldName").withType(PsiTypes.intType())
    }, false);
  }

  @Test
  public void testParamNameNoConflict() {
    changeSignature.get().doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0),
      ParameterInfoImpl.createNew().withName("b").withType(PsiTypes.booleanType())
    }, false);
  }

  @Test
  public void testVarargMethodToNonVarag() {
    changeSignature.get().doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0).withName("i").withType(PsiTypes.intType()),
      ParameterInfoImpl.createNew().withName("b").withType(PsiTypes.booleanType())
    }, false);
  }

  @Test
  public void testParamJavadoc() {
    changeSignature.get().doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.create(1).withName("z").withType(PsiTypes.intType()),
      ParameterInfoImpl.create(0).withName("y").withType(PsiTypes.intType())
    }, false);
  }

  @Test
  public void testParamJavadoc0() {
    changeSignature.get().doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.create(1).withName("z").withType(PsiTypes.intType()),
      ParameterInfoImpl.create(0).withName("y").withType(PsiTypes.intType())
    }, false);
  }

  @Test
  public void testParamJavadoc1() {
    changeSignature.get().doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0).withName("z").withType(PsiTypes.booleanType())
    }, false);
  }

  @Test
  public void testParamJavadoc2() {
    changeSignature.get().doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.createNew().withName("z").withType(PsiTypes.booleanType()),
      ParameterInfoImpl.create(0).withName("a").withType(PsiTypes.booleanType()),
    }, false);
  }

  @Test
  public void testParamJavadoc3() {
    changeSignature.get().doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0).withName("a").withType(PsiTypes.booleanType()),
      ParameterInfoImpl.createNew().withName("b").withType(PsiTypes.booleanType()),
    }, false);
  }

  @Test
  public void testParamJavadoc4() {
    changeSignature.get().doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.createNew().withName("a").withType(PsiTypes.booleanType()),
    }, false);
  }

  @Test
  public void testParamJavadocRenamedReordered() {
    changeSignature.get().doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0).withName("a").withType(PsiTypes.booleanType()),
      ParameterInfoImpl.createNew().withName("c").withType(PsiTypes.booleanType()),
      ParameterInfoImpl.create(1).withName("b1").withType(PsiTypes.booleanType()),
    }, false);
  }

  @Test
  public void testReturnJavadocAdded() {
    changeSignature.get().doTest("int", new ParameterInfoImpl[0], false);
  }

  @Test
  public void testReturnJavadocUnchanged() {
    changeSignature.get().doTest("int", new ParameterInfoImpl[0], false);
  }

  @Test
  public void testJavadocNoNewLineInserted() {
    changeSignature.get().doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0).withName("newArgs").withType(PsiTypes.doubleType()),
    }, false);
  }

  @Test
  public void testSuperCallFromOtherMethod() {
    changeSignature.get().doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.createNew().withName("nnn").withType(PsiTypes.intType()).withDefaultValue("-222"),
    }, false);
  }

  @Test
  public void testUseAnyVariable() {
    changeSignature.get().doTest(null, null, null, method -> new ParameterInfoImpl[]{
      ParameterInfoImpl.createNew().withName("l").withType(changeSignature.get().getFactory().createTypeFromText("List", method))
        .withDefaultValue("null").useAnySingleVariable()
    }, false);
  }

  @Test
  public void testUseThisAsAnyVariable() {
    changeSignature.get().doTest(null, null, null, method -> new ParameterInfoImpl[]{
      ParameterInfoImpl.createNew().withName("l").withType(changeSignature.get().getFactory().createTypeFromText("List", method))
        .withDefaultValue("null").useAnySingleVariable()
    }, false);
  }

  @Test
  public void testUseAnyVariableAndDefault() {
    changeSignature.get().doTest(null, null, null, method -> new ParameterInfoImpl[]{
      ParameterInfoImpl.createNew().withName("c").withType(changeSignature.get().getFactory().createTypeFromText("C", method))
        .withDefaultValue("null").useAnySingleVariable()
    }, false);
  }

  @Test
  public void testRemoveVarargParameter() {
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(() ->
                                                                              changeSignature.get().doTest(null, null, null,
                                                                                     new ParameterInfoImpl[]{ParameterInfoImpl.create(0)},
                                                                                     new ThrownExceptionInfo[0], false)
    );
  }

  @Test
  public void testEnumConstructor() {
    changeSignature.get().doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.createNew().withName("i").withType(PsiTypes.intType()).withDefaultValue("10")
    }, false);
  }

  @Test
  public void testVarargs1() {
    changeSignature.get().doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.createNew().withName("b").withType(PsiTypes.booleanType()).withDefaultValue("true"),
      ParameterInfoImpl.create(0)
    }, false);
  }

  @Test
  public void testVarargs2() {
    changeSignature.get().doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.create(1).withName("i").withType(PsiTypes.intType()),
      ParameterInfoImpl.create(0).withName("b").withType(new PsiEllipsisType(PsiTypes.booleanType()))
    }, false);
  }

  @Test
  public void testJavadocOfDeleted() {
    changeSignature.get().doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0).withName("role").withType(PsiTypes.intType()),
    }, false);
  }

  @Test
  public void testCovariantReturnType() {
    changeSignature.get().doTest(CommonClassNames.JAVA_LANG_RUNNABLE, new ParameterInfoImpl[0], false);
  }

  @Test
  public void testReorderExceptions() {
    changeSignature.get().doTest(null, null, null, new SimpleParameterGen(new ParameterInfoImpl[0]),
           new SimpleExceptionsGen(new ThrownExceptionInfo[]{new JavaThrownExceptionInfo(1), new JavaThrownExceptionInfo(0)}), false);
  }

  @Test
  public void testAlreadyHandled() {
    changeSignature.get().doTest(null, null, null, new SimpleParameterGen(new ParameterInfoImpl[0]),
           method -> new ThrownExceptionInfo[]{
             new JavaThrownExceptionInfo(-1, changeSignature.get().getFactory().createTypeByFQClassName("java.lang.Exception", method.getResolveScope()))
           },
           false
    );
  }

  @Test
  public void testConstructorException() {
    changeSignature.get().doTest(null, null, null, new SimpleParameterGen(new ParameterInfoImpl[0]),
           method -> new ThrownExceptionInfo[]{
             new JavaThrownExceptionInfo(-1, changeSignature.get().getFactory().createTypeByFQClassName("java.io.IOException", method.getResolveScope()))
           },
           false
    );
  }

  @Test
  public void testAddRuntimeException() {
    changeSignature.get().doTest(null, null, null, new SimpleParameterGen(new ParameterInfoImpl[0]),
           method -> new ThrownExceptionInfo[]{
             new JavaThrownExceptionInfo(-1, changeSignature.get().getFactory().createTypeByFQClassName("java.lang.RuntimeException", method.getResolveScope()))
           },
           false
    );
  }

  @Test
  public void testAddException() {
    changeSignature.get().doTest(null, null, null, new SimpleParameterGen(new ParameterInfoImpl[0]),
           method -> new ThrownExceptionInfo[]{
             new JavaThrownExceptionInfo(-1, changeSignature.get().getFactory().createTypeByFQClassName("java.lang.Exception", method.getResolveScope()))
           },
           false
    );
  }

  @Test
  public void testLessSpecificException() {
    changeSignature.get().doTest(null, null, null, new SimpleParameterGen(new ParameterInfoImpl[0]),
           method -> new ThrownExceptionInfo[]{
             new JavaThrownExceptionInfo(0, changeSignature.get().getFactory().createTypeByFQClassName("java.lang.Exception", method.getResolveScope()))
           },
           false
    );
  }

  @Test
  public void testReorderWithVarargs() {  // IDEADEV-26977
    LightJavaCodeInsightTestFixture myFixture = changeSignature.get().getFixture();
    changeSignature.get().doTest(null, ReadAction.computeBlocking(() -> new ParameterInfoImpl[]{
      ParameterInfoImpl.create(1),
      ParameterInfoImpl.create(0).withName("s").withType(changeSignature.get().getFactory().createTypeFromText("java.lang.String...", myFixture.getFile()))
    }), false);
  }

  @Test
  public void testReorderWithVarargsFromSimpleType() {
    LightJavaCodeInsightTestFixture myFixture = changeSignature.get().getFixture();
    changeSignature.get().doTest(null, ReadAction.computeBlocking(() -> new ParameterInfoImpl[]{
      ParameterInfoImpl.create(1),
      ParameterInfoImpl.create(0).withName("s").withType(changeSignature.get().getFactory().createTypeFromText("java.lang.String...", myFixture.getFile()))
    }), false);
  }

  @Test
  public void testIntroduceParameterWithDefaultValueInHierarchy() {
    changeSignature.get().doTest(null, new ParameterInfoImpl[]{ParameterInfoImpl.createNew().withName("i").withType(PsiTypes.intType()).withDefaultValue("0")},
           false);
  }

  @Test
  public void testReorderMultilineMethodParameters() {
    // Inspired by IDEA-54902
    changeSignature.get().doTest(null, new ParameterInfoImpl[]{ParameterInfoImpl.create(1), ParameterInfoImpl.create(0)}, false);
  }

  @Test
  public void testRemoveFirstParameter() {
    changeSignature.get().doTest(null, new ParameterInfoImpl[]{ParameterInfoImpl.create(1)}, false);
  }

  @Test
  public void testReplaceVarargWithArray() {
    changeSignature.get().doTest(null, null, null, method -> new ParameterInfoImpl[]{
      ParameterInfoImpl.create(1).withName("l")
        .withType(changeSignature.get().getFactory().createTypeFromText("List<T>[]", method.getParameterList())).withDefaultValue("null"),
      ParameterInfoImpl.create(0).withName("s").withType(changeSignature.get().getFactory().createTypeFromText("String", method.getParameterList()))
    }, false);
  }

  @Test
  public void testReplaceOldStyleArrayWithVarargs() {
    changeSignature.get().doTest(null, new ParameterInfoImpl[]{ParameterInfoImpl.create(0).withName("a").withType(new PsiEllipsisType(PsiTypes.intType()))},
           false);
  }

  @Test
  public void testReorderParamsOfFunctionalInterface() {
    changeSignature.get().doTest(null, null, null, method -> new ParameterInfoImpl[]{
      ParameterInfoImpl.create(1).withName("b").withType(PsiTypes.intType()),
      ParameterInfoImpl.create(0).withName("a").withType(PsiTypes.booleanType())
    }, false);
  }

  @Test
  public void testReorderParamsOfFunctionalInterfaceExpandMethodReference() {
    GenParams genParams = method -> new ParameterInfoImpl[]{
      ParameterInfoImpl.create(1).withName("b").withType(PsiTypes.intType()),
      ParameterInfoImpl.create(0).withName("a").withType(PsiTypes.intType())
    };
    changeSignature.get().doTest(null, null, null, genParams, new SimpleExceptionsGen(), false, true);
  }

  @Test
  public void testAddParenthesisForLambdaParameterList() {
    GenParams genParams = method -> new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0).withName("a").withType(PsiTypes.intType()),
      ParameterInfoImpl.createNew().withName("b").withType(PsiTypes.intType())
    };
    changeSignature.get().doTest(null, null, null, genParams, new SimpleExceptionsGen(), false, true);
  }

  @Test
  public void testAddParenthesisForLambdaParameterListDeleteTheOnlyOne() {
    GenParams genParams = method -> new ParameterInfoImpl[0];
    changeSignature.get().doTest(null, null, null, genParams, new SimpleExceptionsGen(), false, true);
  }

  @Test
  public void testExpandMethodReferenceToDeleteParameter() {
    GenParams genParams = method -> new ParameterInfoImpl[0];
    changeSignature.get().doTest(null, null, null, genParams, new SimpleExceptionsGen(), false, true);
  }

  @Test
  public void testRenameMethodUsedInMethodReference() {
    GenParams genParams = method -> new ParameterInfoImpl[]{ParameterInfoImpl.create(0).withName("a").withType(PsiTypes.intType())};
    changeSignature.get().doTest(PsiModifier.PRIVATE, "alwaysFalse", null, genParams, new SimpleExceptionsGen(), false, false);
  }

  @Test
  public void testMethodParametersAlignmentAfterMethodNameChange() {
    getCommonSettings().ALIGN_MULTILINE_PARAMETERS = true;
    getCommonSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    changeSignature.get().doTest(null, "test123asd", null, new SimpleParameterGen(), new SimpleExceptionsGen(), false);
  }

  @Test
  public void testMethodParametersAlignmentAfterMethodVisibilityChange() {
    getCommonSettings().ALIGN_MULTILINE_PARAMETERS = true;
    getCommonSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    changeSignature.get().doTest(PsiModifier.PROTECTED, null, null, new SimpleParameterGen(), new SimpleExceptionsGen(), false);
  }

  @Test
  public void testMethodParametersAlignmentAfterMethodReturnTypeChange() {
    getCommonSettings().ALIGN_MULTILINE_PARAMETERS = true;
    getCommonSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    changeSignature.get().doTest(null, null, "Exception", new SimpleParameterGen(), new SimpleExceptionsGen(), false);
  }

  @Test
  public void testRemoveOverride() {
    changeSignature.get().doTest(null, null, null, new ParameterInfoImpl[0], new ThrownExceptionInfo[0], false);
  }

  @Test
  public void testPreserveOverride() {
    changeSignature.get().doTest(null, null, null, new ParameterInfoImpl[0], new ThrownExceptionInfo[0], false);
  }

  @Test
  public void testKeepTryWithResources() {
    changeSignature.get().doTest(null, null, null, new ParameterInfoImpl[0], new ThrownExceptionInfo[0], false);
  }

  @Test
  public void testVisibilityOfOverriddenMethod() {
    changeSignature.get().doTest(PsiModifier.PACKAGE_LOCAL, "foo", "void", new ParameterInfoImpl[0], new ThrownExceptionInfo[0], false);
  }

  @Test
  public void testRemoveExceptions() {
    changeSignature.get().doTest(null, null, "void", new SimpleParameterGen(), new SimpleExceptionsGen(), false);
  }

  @Test
  public void testPropagateParameter() {
    LightJavaCodeInsightTestFixture myFixture = changeSignature.get().getFixture();
    String basePath = RELATIVE_PATH + myFixture.getTestName(false);
    myFixture.configureByFile(basePath + ".java");
    myFixture.runInCommand(() -> {
      final PsiElement targetElement = TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED);
      assertInstanceOf(PsiMethod.class, targetElement, "<caret> is not on method name");
      PsiMethod method = (PsiMethod)targetElement;
      final PsiClass containingClass = method.getContainingClass();
      assertNotNull(containingClass);
      final PsiMethod[] callers = containingClass.findMethodsByName("caller", false);
      assertTrue(callers.length > 0);
      final PsiMethod caller = callers[0];
      final HashSet<PsiMethod> propagateParametersMethods = new HashSet<>();
      propagateParametersMethods.add(caller);
      final PsiParameter[] parameters = method.getParameterList().getParameters();
      new ChangeSignatureProcessor(myFixture.getProject(), method, false, null, method.getName(),
                                   CanonicalTypes.createTypeWrapper(PsiTypes.voidType()), new ParameterInfoImpl[]{
        ParameterInfoImpl.create(0).withName(parameters[0].getName()).withType(parameters[0].getType()),
        ParameterInfoImpl.createNew().withName("b").withType(PsiTypes.booleanType())}, null, propagateParametersMethods, null
      ).run();
      myFixture.checkResultByFile(basePath + "_after.java");
    });
  }

  @Test
  public void testPropagateParameterWithOverrider() {
    LightJavaCodeInsightTestFixture myFixture = changeSignature.get().getFixture();
    String basePath = RELATIVE_PATH + myFixture.getTestName(false);
    myFixture.configureByFile(basePath + ".java");
    myFixture.runInCommand(() -> {
      final PsiElement targetElement = TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED);
      assertInstanceOf(PsiMethod.class, targetElement, "<caret> is not on method name");
      PsiMethod method = (PsiMethod)targetElement;
      final PsiClass containingClass = method.getContainingClass();
      assertNotNull(containingClass);
      final PsiMethod[] callers = containingClass.findMethodsByName("caller", false);
      assertTrue(callers.length > 0);
      final PsiMethod caller = callers[0];
      final HashSet<PsiMethod> propagateParametersMethods = new HashSet<>();
      propagateParametersMethods.add(caller);
      final PsiParameter[] parameters = method.getParameterList().getParameters();
      new ChangeSignatureProcessor(myFixture.getProject(), method, false, null, method.getName(),
                                   CanonicalTypes.createTypeWrapper(PsiTypes.voidType()), new ParameterInfoImpl[]{
        ParameterInfoImpl.create(0).withName(parameters[0].getName()).withType(parameters[0].getType()),
        ParameterInfoImpl.createNew().withName("b").withType(PsiTypes.booleanType()).withDefaultValue("true")}, null,
                                   propagateParametersMethods, null
      ).run();
      myFixture.checkResultByFile(basePath + "_after.java");
    });
  }

  @Test
  public void testTypeAnnotationsAllAround() {
    //String[] ps = {"@TA(1) int @TA(2) []", "java.util.@TA(4) List<@TA(5) Class<@TA(6) ?>>", "@TA(7) String @TA(8) ..."};
    //String[] ex = {"@TA(42) IllegalArgumentException", "java.lang.@TA(43) IllegalStateException"};
    //changeSignature.get().doTest("java.util.@TA(0) List<@TA(1) C.@TA(1) Inner>", ps, ex, false);
    String[] ps = {"@TA(2) int @TA(3) []", "@TA(4) List<@TA(5) Class<@TA(6) ?>>", "@TA(7) String @TA(8) ..."};
    String[] ex = ArrayUtil.EMPTY_STRING_ARRAY;
    changeSignature.get().doTest("@TA(0) List<@TA(1) Inner>", ps, ex, false);
  }

  @Test
  public void testContractUpdate() {
    GenParams genParams = method -> {
      PsiClassType stringType = PsiType.getJavaLangString(method.getManager(), method.getResolveScope());
      return new ParameterInfoImpl[]{
        ParameterInfoImpl.create(2).withName("a").withType(stringType),
        ParameterInfoImpl.create(0).withName("b").withType(stringType),
        ParameterInfoImpl.createNew().withName("c").withType(stringType)
      };
    };
    changeSignature.get().doTest(null, null, null, genParams, new SimpleExceptionsGen(), false, false);
  }

  @Test
  public void testRecordHeaderDeleteRename() {
    changeSignature.get().doTest(null, null, null, method -> {
      return new ParameterInfoImpl[]{
        ParameterInfoImpl.create(1).withName("yyy").withType(PsiTypes.longType())
      };
    }, false);
  }

  @Test
  public void testRecordHeaderDeleteRename2() {
    LightJavaCodeInsightTestFixture myFixture = changeSignature.get().getFixture();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(myFixture.getProject());
    final PsiType pointType = ReadAction.computeBlocking(() -> facade.getElementFactory().createTypeFromText("Point", null));
    changeSignature.get().doTest(null, null, null, method -> {
      return new ParameterInfoImpl[]{
        ParameterInfoImpl.create(1).withName("p2").withType(pointType)
      };
    }, false);
  }

  @Test
  public void testRecordCanonicalConstructorRename() {
    changeSignature.get().doTest(null, null, null, method -> {
      return new ParameterInfoImpl[]{
        ParameterInfoImpl.create(0).withName("y").withType(PsiTypes.intType()),
        ParameterInfoImpl.create(1).withName("z").withType(PsiTypes.intType()),
        ParameterInfoImpl.create(2).withName("x").withType(PsiTypes.intType())
      };
    }, false);
  }

  @Test
  public void testRecordCanonicalConstructorRename2() {
    LightJavaCodeInsightTestFixture myFixture = changeSignature.get().getFixture();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(myFixture.getProject());
    final PsiType pointType = ReadAction.computeBlocking(() -> facade.getElementFactory().createTypeFromText("Point", null));
    changeSignature.get().doTest(null, null, null, method -> {
      return new ParameterInfoImpl[]{
        ParameterInfoImpl.create(0).withName("point2").withType(pointType),
        ParameterInfoImpl.create(1).withName("point1").withType(pointType),
        ParameterInfoImpl.create(2).withName("i").withType(PsiTypes.intType())
      };
    }, false);
  }

  @Test
  public void testRecordCanonicalConstructorReorder() {
    changeSignature.get().doTest(null, null, null, method -> {
      return new ParameterInfoImpl[]{
        ParameterInfoImpl.create(1).withName("y").withType(PsiTypes.intType()),
        ParameterInfoImpl.create(2).withName("z").withType(PsiTypes.intType()),
        ParameterInfoImpl.create(0).withName("x").withType(PsiTypes.intType())
      };
    }, false);
  }

  @Test
  public void testRecordCanonicalConstructorReorder2() {
    LightJavaCodeInsightTestFixture myFixture = changeSignature.get().getFixture();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(myFixture.getProject());
    final PsiType pointType = ReadAction.computeBlocking(() -> facade.getElementFactory().createTypeFromText("Point", null));
    changeSignature.get().doTest(null, null, null, method -> {
      return new ParameterInfoImpl[]{
        ParameterInfoImpl.create(2).withName("i").withType(PsiTypes.intType()),
        ParameterInfoImpl.create(1).withName("point2").withType(pointType),
        ParameterInfoImpl.create(0).withName("point1").withType(pointType),
      };
    }, false);
  }

  @Test
  public void testRecordCanonicalConstructorAddParameter() {
    changeSignature.get().doTest(null, null, null, method -> {
      return new ParameterInfoImpl[]{
        ParameterInfoImpl.create(0).withName("x").withType(PsiTypes.intType()),
        ParameterInfoImpl.create(-1).withName("y").withType(PsiTypes.intType())
      };
    }, false);
  }

  @Test
  public void testRecordCanonicalConstructorAddParameter2() {
    LightJavaCodeInsightTestFixture myFixture = changeSignature.get().getFixture();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(myFixture.getProject());
    final PsiType pointType = ReadAction.computeBlocking(() -> facade.getElementFactory().createTypeFromText("Point", null));
    changeSignature.get().doTest(null, null, null, method -> {
      return new ParameterInfoImpl[]{
        ParameterInfoImpl.create(0).withName("point1").withType(pointType),
        ParameterInfoImpl.create(1).withName("point2").withType(pointType),
        ParameterInfoImpl.create(-1).withName("i").withType(PsiTypes.intType())
      };
    }, false);
  }

  @Test
  public void testRecordComponentWithImportToBeAdded() {
    changeSignature.get().doTest(null, null, null, method -> {
      return new ParameterInfoImpl[]{
        ParameterInfoImpl.create(-1).withName("y").withType(changeSignature.get().getFactory().createTypeFromText("java.util.List<java.lang.String>", method))
      };
    }, false);
  }

  @Test
  public void testRecordCanonicalConstructorMissingHeader() {
    changeSignature.get().doTest(null, null, null, method -> {
      return new ParameterInfoImpl[]{
        ParameterInfoImpl.create(-1).withName("x").withType(PsiTypes.intType())
      };
    }, false);
  }

  @Test
  public void testRemoveAnnotation() {
    changeSignature.get().doTest(null, null, null, method -> new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0).withName("x").withType(PsiTypes.intType())
    }, false);
  }

  @Test
  public void testAddReturnAnnotation() {
    changeSignature.get().doTest(null, null, "java.lang.@org.jetbrains.annotations.NotNull String", method -> new ParameterInfoImpl[0], false);
  }

  @Test
  public void testMultilineJavadoc() { // IDEA-281568
    changeSignature.get().doTest(null, null, null, method -> new ParameterInfoImpl[]{
      ParameterInfoImpl.create(1).withType(PsiTypes.intType()).withName("b"),
      ParameterInfoImpl.create(0).withType(PsiTypes.intType()).withName("a"),
      ParameterInfoImpl.create(2).withType(PsiTypes.intType()).withName("c"),
    }, false);
  }

  @Test
  public void testPreserveEmptyTrailingLeadingLinesJavadoc() {
    changeSignature.get().doTest(null, null, null, method -> new ParameterInfoImpl[]{
      ParameterInfoImpl.create(1).withType(PsiTypes.intType()).withName("b"),
      ParameterInfoImpl.create(0).withType(PsiTypes.intType()).withName("a"),
      ParameterInfoImpl.create(2).withType(PsiTypes.intType()).withName("c"),
    }, false);
  }

  @Test
  public void testMultilineJavadocWithoutFormatting() { // IDEA-281568
    LightJavaCodeInsightTestFixture myFixture = changeSignature.get().getFixture();
    JavaCodeStyleSettings.getInstance(myFixture.getProject()).ENABLE_JAVADOC_FORMATTING = false;
    changeSignature.get().doTest(null, null, null, method -> new ParameterInfoImpl[]{
      ParameterInfoImpl.create(1).withType(PsiTypes.intType()).withName("b"),
      ParameterInfoImpl.create(0).withType(PsiTypes.intType()).withName("a"),
      ParameterInfoImpl.create(2).withType(PsiTypes.intType()).withName("c"),
    }, false);
  }

  @Test
  public void testJavadocNotBrokenAfterDelete() { // IDEA-139879
    changeSignature.get().doTest(null, null, null, method -> new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0).withType(PsiTypes.intType()).withName("i1")
    }, false);
  }

  @Test
  public void testNoGapsInParameterTags() { // IDEA-139879
    changeSignature.get().doTest(null, null, null, method -> new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0).withType(PsiTypes.intType()).withName("b"),
      ParameterInfoImpl.create(1).withType(PsiTypes.longType()).withName("a"),
      ParameterInfoImpl.create(2).withType(PsiTypes.booleanType()).withName("c"),
      ParameterInfoImpl.createNew().withType(PsiTypes.shortType()).withName("d"),
    }, false);
  }

  @Test
  public void testVarargToArray() { // IDEA-318626
    changeSignature.get().doTest(null, null, null, method -> new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0).withName("x"),
      ParameterInfoImpl.create(1).withName("args").withType(
        method.getParameterList().getParameter(1).getType().getDeepComponentType().createArrayType())
    }, false);
  }

  @Test
  public void testEnumVarargToArray() {
    changeSignature.get().doTest(null, null, null, method -> new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0).withName("x"),
      ParameterInfoImpl.create(1).withName("args").withType(
        method.getParameterList().getParameter(1).getType().getDeepComponentType().createArrayType())
    }, false);
  }

  @Test
  public void testDefCtorVarargToArray() {
    changeSignature.get().doTest(null, null, null, method -> new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0).withName("args").withType(
        method.getParameterList().getParameter(0).getType().getDeepComponentType().createArrayType())
    }, false);
  }

  @Test
  public void testArrayToVararg() { // IDEA-318626
    changeSignature.get().doTest(null, null, null, method -> new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0).withName("x"),
      ParameterInfoImpl.create(1).withName("args").withType(
        new PsiEllipsisType(method.getParameterList().getParameter(1).getType().getDeepComponentType()))
    }, false);
  }

  @Test
  public void testVarargToArrayReorder() { // IDEA-318626
    changeSignature.get().doTest(null, null, null, method -> new ParameterInfoImpl[]{
      ParameterInfoImpl.create(1).withName("args").withType(
        method.getParameterList().getParameter(1).getType().getDeepComponentType().createArrayType()),
      ParameterInfoImpl.create(0).withName("x")
    }, false);
  }

  /* Markdown javadoc variant */
  @Test
  public void testParamJavadocMarkdown2() {
    changeSignature.get().doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.createNew().withName("z").withType(PsiTypes.booleanType()),
      ParameterInfoImpl.create(0).withName("a").withType(PsiTypes.booleanType()),
    }, false);
  }

  @Test
  public void testRecordHeaderDeleteRenameMarkdown() {
    changeSignature.get().doTest(null, null, null, method -> {
      return new ParameterInfoImpl[]{
        ParameterInfoImpl.create(1).withName("yyy").withType(PsiTypes.longType())
      };
    }, false);
  }

  @Test
  public void testRecordCanonicalConstructorReorderMarkdown2() {
    LightJavaCodeInsightTestFixture myFixture = changeSignature.get().getFixture();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(myFixture.getProject());
    final PsiType pointType = ReadAction.computeBlocking(() -> facade.getElementFactory().createTypeFromText("Point", null));
    changeSignature.get().doTest(null, null, null, method -> {
      return new ParameterInfoImpl[]{
        ParameterInfoImpl.create(2).withName("i").withType(PsiTypes.intType()),
        ParameterInfoImpl.create(1).withName("point2").withType(pointType),
        ParameterInfoImpl.create(0).withName("point1").withType(pointType),
      };
    }, false);
  }

  @Test
  public void testJavadocMarkdownOfDeleted() {
    changeSignature.get().doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0).withName("role").withType(PsiTypes.intType()),
    }, false);
  }

  @Test
  public void testRecordCanonicalConstructorAddParameterMarkdown2() {
    LightJavaCodeInsightTestFixture myFixture = changeSignature.get().getFixture();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(myFixture.getProject());
    final PsiType pointType = ReadAction.computeBlocking(() -> facade.getElementFactory().createTypeFromText("Point", null));
    changeSignature.get().doTest(null, null, null, method -> {
      return new ParameterInfoImpl[]{
        ParameterInfoImpl.create(0).withName("point1").withType(pointType),
        ParameterInfoImpl.create(1).withName("point2").withType(pointType),
        ParameterInfoImpl.create(-1).withName("i").withType(PsiTypes.intType())
      };
    }, false);
  }

  @Test
  public void testJavadocMarkdownNoNewLineInserted() {
    changeSignature.get().doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0).withName("newArgs").withType(PsiTypes.doubleType()),
    }, false);
  }

  @Test
  public void testMultilineJavadocMarkdownWithoutFormatting() { // IDEA-281568
    LightJavaCodeInsightTestFixture myFixture = changeSignature.get().getFixture();
    JavaCodeStyleSettings.getInstance(myFixture.getProject()).ENABLE_JAVADOC_FORMATTING = false;
    changeSignature.get().doTest(null, null, null, method -> new ParameterInfoImpl[]{
      ParameterInfoImpl.create(1).withType(PsiTypes.intType()).withName("b"),
      ParameterInfoImpl.create(0).withType(PsiTypes.intType()).withName("a"),
      ParameterInfoImpl.create(2).withType(PsiTypes.intType()).withName("c"),
    }, false);
  }

  @Test
  public void testParamJavadocMarkdown1() {
    changeSignature.get().doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0).withName("z").withType(PsiTypes.booleanType())
    }, false);
  }

  @Test
  public void testParamJavadocMarkdown0() {
    changeSignature.get().doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.create(1).withName("z").withType(PsiTypes.intType()),
      ParameterInfoImpl.create(0).withName("y").withType(PsiTypes.intType())
    }, false);
  }

  @Test
  public void testReturnJavadocMarkdownAdded() {
    changeSignature.get().doTest("int", new ParameterInfoImpl[0], false);
  }

  @Test
  public void testRecordCanonicalConstructorRenameMarkdown() {
    changeSignature.get().doTest(null, null, null, method -> {
      return new ParameterInfoImpl[]{
        ParameterInfoImpl.create(0).withName("y").withType(PsiTypes.intType()),
        ParameterInfoImpl.create(1).withName("z").withType(PsiTypes.intType()),
        ParameterInfoImpl.create(2).withName("x").withType(PsiTypes.intType())
      };
    }, false);
  }

  @Test
  public void testParamJavadocMarkdown() {
    changeSignature.get().doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.create(1).withName("z").withType(PsiTypes.intType()),
      ParameterInfoImpl.create(0).withName("y").withType(PsiTypes.intType())
    }, false);
  }

  @Test
  public void testRecordCanonicalConstructorAddParameterMarkdown() {
    changeSignature.get().doTest(null, null, null, method -> {
      return new ParameterInfoImpl[]{
        ParameterInfoImpl.create(0).withName("x").withType(PsiTypes.intType()),
        ParameterInfoImpl.create(-1).withName("y").withType(PsiTypes.intType())
      };
    }, false);
  }

  @Test
  public void testParamJavadocMarkdown4() {
    changeSignature.get().doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.createNew().withName("a").withType(PsiTypes.booleanType()),
    }, false);
  }

  @Test
  public void testSCRMarkdown40895() {
    changeSignature.get().doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0).withName("y").withType(PsiTypes.intType()),
      ParameterInfoImpl.create(1).withName("b").withType(PsiTypes.booleanType())
    }, false);
  }

  @Test
  public void testReturnJavadocMarkdownUnchanged() {
    changeSignature.get().doTest("int", new ParameterInfoImpl[0], false);
  }

  @Test
  public void testRecordComponentWithImportToBeAddedMarkdown() {
    changeSignature.get().doTest(null, null, null, method -> {
      return new ParameterInfoImpl[]{
        ParameterInfoImpl.create(-1).withName("y").withType(changeSignature.get().getFactory().createTypeFromText("java.util.List<java.lang.String>", method))
      };
    }, false);
  }

  @Test
  public void testMethodParametersAlignmentAfterMethodReturnTypeChangeMarkdown() {
    getCommonSettings().ALIGN_MULTILINE_PARAMETERS = true;
    getCommonSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    changeSignature.get().doTest(null, null, "Exception", new SimpleParameterGen(), new SimpleExceptionsGen(), false);
  }

  @Test
  public void testParamJavadocMarkdown3() {
    changeSignature.get().doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0).withName("a").withType(PsiTypes.booleanType()),
      ParameterInfoImpl.createNew().withName("b").withType(PsiTypes.booleanType()),
    }, false);
  }

  @Test
  public void testRecordHeaderDeleteRenameMarkdown2() {
    LightJavaCodeInsightTestFixture myFixture = changeSignature.get().getFixture();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(myFixture.getProject());
    final PsiType pointType = ReadAction.computeBlocking(() -> facade.getElementFactory().createTypeFromText("Point", null));
    changeSignature.get().doTest(null, null, null, method -> {
      return new ParameterInfoImpl[]{
        ParameterInfoImpl.create(1).withName("p2").withType(pointType)
      };
    }, false);
  }

  @Test
  public void testParamJavadocMarkdownRenamedReordered() {
    changeSignature.get().doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0).withName("a").withType(PsiTypes.booleanType()),
      ParameterInfoImpl.createNew().withName("c").withType(PsiTypes.booleanType()),
      ParameterInfoImpl.create(1).withName("b1").withType(PsiTypes.booleanType()),
    }, false);
  }

  @Test
  public void testJavadocMarkdownGenericsLink() {
    changeSignature.get().doTest(null, ReadAction.computeBlocking(() -> new ParameterInfoImpl[]{
      ParameterInfoImpl.createNew().withName("y").withType(changeSignature.get().getFactory().createTypeFromText("java.util.List<java.lang.String>", null)),
      ParameterInfoImpl.create(0).withName("a").withType(PsiTypes.booleanType())
    }), false);
  }

  @Test
  public void testRecordCanonicalConstructorReorderMarkdown() {
    changeSignature.get().doTest(null, null, null, method -> {
      return new ParameterInfoImpl[]{
        ParameterInfoImpl.create(1).withName("y").withType(PsiTypes.intType()),
        ParameterInfoImpl.create(2).withName("z").withType(PsiTypes.intType()),
        ParameterInfoImpl.create(0).withName("x").withType(PsiTypes.intType())
      };
    }, false);
  }

  @Test
  public void testMultilineJavadocMarkdown() { // IDEA-281568
    changeSignature.get().doTest(null, null, null, method -> new ParameterInfoImpl[]{
      ParameterInfoImpl.create(1).withType(PsiTypes.intType()).withName("b"),
      ParameterInfoImpl.create(0).withType(PsiTypes.intType()).withName("a"),
      ParameterInfoImpl.create(2).withType(PsiTypes.intType()).withName("c"),
    }, false);
  }

  @Test
  public void testPreserveEmptyTrailingLeadingLinesJavadocMarkdown() {
      changeSignature.get().doTest(null, null, null, method -> new ParameterInfoImpl[]{
        ParameterInfoImpl.create(1).withType(PsiTypes.intType()).withName("b"),
        ParameterInfoImpl.create(0).withType(PsiTypes.intType()).withName("a"),
        ParameterInfoImpl.create(2).withType(PsiTypes.intType()).withName("c"),
      }, false);
  }

  @Test
  public void testNoGapsInParameterTagsMarkdown() { // IDEA-139879
    changeSignature.get().doTest(null, null, null, method -> new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0).withType(PsiTypes.intType()).withName("b"),
      ParameterInfoImpl.create(1).withType(PsiTypes.longType()).withName("a"),
      ParameterInfoImpl.create(2).withType(PsiTypes.booleanType()).withName("c"),
      ParameterInfoImpl.createNew().withType(PsiTypes.shortType()).withName("d"),
    }, false);
  }

  @Test
  public void testRecordCanonicalConstructorRenameMarkdown2() {
    LightJavaCodeInsightTestFixture myFixture = changeSignature.get().getFixture();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(myFixture.getProject());
    final PsiType pointType = ReadAction.computeBlocking(() -> facade.getElementFactory().createTypeFromText("Point", null));
    changeSignature.get().doTest(null, null, null, method -> {
      return new ParameterInfoImpl[]{
        ParameterInfoImpl.create(0).withName("point2").withType(pointType),
        ParameterInfoImpl.create(1).withName("point1").withType(pointType),
        ParameterInfoImpl.create(2).withName("i").withType(PsiTypes.intType())
      };
    }, false);
  }

  @Test
  public void testJavadocMarkdownNotBrokenAfterDelete() { // IDEA-139879
    changeSignature.get().doTest(null, null, null, method -> new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0).withType(PsiTypes.intType()).withName("i1")
    }, false);
  }

  @Test
  public void testConflictsForFieldRecord() {
    LightJavaCodeInsightTestFixture myFixture = changeSignature.get().getFixture();
    assertConflict(() -> {
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(myFixture.getProject());
      final PsiType pointType = ReadAction.computeBlocking(() -> facade.getElementFactory().createTypeFromText("String", null));
      changeSignature.get().doTest(null, null, null, method -> {
        return new ParameterInfoImpl[]{
          ParameterInfoImpl.create(1).withName("b").withType(pointType)
        };
      }, false);
    }, "Record component 'a' is used");
  }

  @Test
  public void testConflictsForGetRecord() {
    LightJavaCodeInsightTestFixture myFixture = changeSignature.get().getFixture();
    assertConflict(() -> {
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(myFixture.getProject());
      final PsiType pointType = ReadAction.computeBlocking(() -> facade.getElementFactory().createTypeFromText("String", null));
      changeSignature.get().doTest(null, null, null, method -> {
        return new ParameterInfoImpl[]{
          ParameterInfoImpl.create(1).withName("b").withType(pointType)
        };
      }, false);
    }, "Record component 'a' is used");
  }

  @Test
  public void testImplicitDefaultConstructor() {
    changeSignature.get().doTest(null, null, null, method -> {
      return new ParameterInfoImpl[]{
        ParameterInfoImpl.createNew().withName("i").withType(PsiTypes.intType())
      };
    }, false);
  }

  @Test
  public void testWithoutConflictsForGet() {
    LightJavaCodeInsightTestFixture myFixture = changeSignature.get().getFixture();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(myFixture.getProject());
    final PsiType pointType = ReadAction.computeBlocking(() -> facade.getElementFactory().createTypeFromText("String", null));
    changeSignature.get().doTest(null, null, null, method -> {
      return new ParameterInfoImpl[]{
        ParameterInfoImpl.create(1).withName("b").withType(pointType)
      };
    }, false);
  }

  @Test
  public void testConflictsSwitchUsedDeconstruction() {
    LightJavaCodeInsightTestFixture myFixture = changeSignature.get().getFixture();
    assertConflict(() -> {
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(myFixture.getProject());
      final PsiType pointType = ReadAction.computeBlocking(() -> facade.getElementFactory().createTypeFromText("String", null));
      changeSignature.get().doTest(null, null, null, method -> {
        return new ParameterInfoImpl[]{
          ParameterInfoImpl.create(1).withName("b").withType(pointType)
        };
      }, false);
    }, "Record component 'a' is used");
  }

  @Test
  public void testWithoutConflictsSwitchUsedDeconstruction() {
    changeSignature.get().doTest(null, null, null, method -> {
      return new ParameterInfoImpl[]{
        ParameterInfoImpl.create(0).withName("a").withType(PsiTypes.intType())
      };
    }, false);
  }

  @Test
  public void testConflictsSwitchNarrowedDeconstruction() {
    assertConflict(() -> {
      changeSignature.get().doTest(null, null, null, method -> {
        return new ParameterInfoImpl[]{
          ParameterInfoImpl.create(0).withName("a").withType(PsiTypes.intType())
        };
      }, false);
    }, "Record component 'b' is used");
  }

  @Test
  public void testWithoutConflictsSwitchExtendedDeconstruction() {
    changeSignature.get().doTest(null, null, null, method -> {
      return new ParameterInfoImpl[]{
        ParameterInfoImpl.create(0).withName("a").withType(PsiTypes.intType())
      };
    }, false);
  }

  @Test
  public void testConflictsSwitchNestedDeconstruction() {
    assertConflict(() -> {
      changeSignature.get().doTest(null, null, null, method -> {
        return new ParameterInfoImpl[]{
          ParameterInfoImpl.create(0).withName("x").withType(PsiTypes.intType())
        };
      }, false);
    }, "Record component 'y' is used");
  }

  @Test
  public void testWithoutConflictsSwitchNestedDeconstruction() {
    changeSignature.get().doTest(null, null, null, method -> {
      return new ParameterInfoImpl[]{
        ParameterInfoImpl.create(0).withName("x").withType(PsiTypes.intType())
      };
    }, false);
  }

  @Test
  public void testConflictsSwitchNestedDeconstructionDifTypes() {
    assertConflict(() -> {
      changeSignature.get().doTest(null, null, null, method -> {
        return new ParameterInfoImpl[]{
          ParameterInfoImpl.create(0).withName("x").withType(PsiTypes.intType())
        };
      }, false);
    }, "Record component 'x' is used");
  }

  @SuppressWarnings("CatchMayIgnoreException")
  private static void assertConflict(@NotNull Runnable runnable, @NotNull String expectedMessage) {
    try {
      runnable.run();
      fail("Conflict expected");
    }
    catch (RuntimeException e) {
      Assertions.assertThat(e).isInstanceOf(BaseRefactoringProcessor.ConflictsInTestsException.class);
      Assertions.assertThat(e.getMessage()).isEqualTo(expectedMessage);
    }
  }
}
