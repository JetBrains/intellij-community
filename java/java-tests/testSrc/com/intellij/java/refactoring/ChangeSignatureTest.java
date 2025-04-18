// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.JavaThrownExceptionInfo;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.refactoring.changeSignature.ThrownExceptionInfo;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.util.ArrayUtil;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;

public class ChangeSignatureTest extends ChangeSignatureBaseTest {
  private CommonCodeStyleSettings getCommonSettings() {
    return getCurrentCodeStyleSettings().getCommonSettings(JavaLanguage.INSTANCE);
  }

  public void testSimple() {
    doTest(null, null, null, new ParameterInfoImpl[0], new ThrownExceptionInfo[0], false);
  }

  public void testParameterReorder() {
    doTest(null, new ParameterInfoImpl[]{ParameterInfoImpl.create(1), ParameterInfoImpl.create(0)}, false);
  }

  public void testWarnAboutContract() {
    assertConflict(() -> {
      doTest(null, new ParameterInfoImpl[]{ParameterInfoImpl.create(1)}, false);
    }, "@Contract annotation cannot be updated automatically: Parameter 'i' was deleted, but contract clause 'null, _ -> fail' depends on it");
  }

  public void testWarnAboutAssigningWeakerAccessPrivileges() {
    assertConflict(() -> {
      doTest(PsiModifier.PRIVATE, null, null, new ParameterInfoImpl[0], new ThrownExceptionInfo[0], false);
    }, "method <b><code>f()</code></b> will have incompatible access privileges with super method <b><code>X.f()</code></b>");
  }

  public void testDelegateWithoutChangesWarnAboutSameMethodInClass() {
    assertConflict(() -> {
      doTest(null, new ParameterInfoImpl[0], true);
    }, "Method <b><code>m()</code></b> is already defined in class <b><code>A</code></b>");
  }

  public void testDuplicatedSignatureInInheritor() {
    assertConflict(() -> {
      doTest(null, new ParameterInfoImpl[]{ParameterInfoImpl.createNew().withName("i").withType(PsiTypes.intType())}, true);
    }, "Method <b><code>foo(int)</code></b> is already defined in class <b><code>B</code></b>");
  }

  public void testConflictForUsedParametersInMethodBody() {
    assertConflict(() -> {
      doTest(null, new ParameterInfoImpl[0], true);
    }, "Parameter <b><code>i</code></b> is used in method body");
  }

  public void testNoConflictForSuperCallDelegation() {
    doTest(null, new ParameterInfoImpl[0], false);
  }

  public void testGenericTypes() {
    doTest(null, null, "T", method -> new ParameterInfoImpl[]{
      ParameterInfoImpl.createNew().withName("x").withType(myFactory.createTypeFromText("T", method.getParameterList())).withDefaultValue(
        "null"),
      ParameterInfoImpl.createNew().withName("y")
        .withType(myFactory.createTypeFromText("C<T>", method.getParameterList())).withDefaultValue("null")
    }, false);
  }

  public void testGenericTypesInOldParameters() {
    doTest(null, null, null, method -> new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0).withName("t").withType(myFactory.createTypeFromText("T", method)).withDefaultValue(null)
    }, false);
  }

  public void testTypeParametersInMethod() {
    doTest(null, null, null, method -> new ParameterInfoImpl[]{
      ParameterInfoImpl.createNew().withName("t").withType(myFactory.createTypeFromText("T", method.getParameterList())).withDefaultValue(
        "null"),
      ParameterInfoImpl.createNew().withName("u").withType(myFactory.createTypeFromText("U", method.getParameterList())).withDefaultValue(
        "null"),
      ParameterInfoImpl.createNew().withName("cu")
        .withType(myFactory.createTypeFromText("C<U>", method.getParameterList())).withDefaultValue("null")
    }, false);
  }

  public void testDefaultConstructor() {
    doTest(null,
           new ParameterInfoImpl[]{
             ParameterInfoImpl.createNew().withName("j").withType(PsiTypes.intType()).withDefaultValue("27")
           }, false
    );
  }

  public void testGenerateDelegate() {
    doTest(null,
           new ParameterInfoImpl[]{
             ParameterInfoImpl.createNew().withName("i").withType(PsiTypes.intType()).withDefaultValue("27")
           }, true
    );
  }

  public void testGenerateDelegateForAbstract() {
    doTest(null,
           new ParameterInfoImpl[]{
             ParameterInfoImpl.createNew().withName("i").withType(PsiTypes.intType()).withDefaultValue("27")
           }, true
    );
  }

  public void testGenerateDelegateWithReturn() {
    doTest(null,
           new ParameterInfoImpl[]{
             ParameterInfoImpl.createNew().withName("i").withType(PsiTypes.intType()).withDefaultValue("27")
           }, true
    );
  }

  public void testGenerateDelegateWithParametersReordering() {
    doTest(null,
           new ParameterInfoImpl[]{
             ParameterInfoImpl.create(1),
             ParameterInfoImpl.createNew().withName("c").withType(PsiTypes.charType()).withDefaultValue("'a'"),
             ParameterInfoImpl.create(0).withName("j").withType(PsiTypes.intType())
           }, true
    );
  }

  public void testGenerateDelegateConstructor() {
    doTest(null, new ParameterInfoImpl[0], true);
  }

  public void testGenerateDelegateDefaultConstructor() {
    doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.createNew().withName("i").withType(PsiTypes.intType()).withDefaultValue("27")
    }, true);
  }

  public void testSCR40895() {
    doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0).withName("y").withType(PsiTypes.intType()),
      ParameterInfoImpl.create(1).withName("b").withType(PsiTypes.booleanType())
    }, false);
  }

  public void testJavadocGenericsLink() {
    doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.createNew().withName("y").withType(myFactory.createTypeFromText("java.util.List<java.lang.String>", null)),
      ParameterInfoImpl.create(0).withName("a").withType(PsiTypes.booleanType())
    }, false);
  }

  public void testParamNameSameAsFieldName() {
    doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0).withName("fieldName").withType(PsiTypes.intType())
    }, false);
  }

  public void testParamNameNoConflict() {
    doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0),
      ParameterInfoImpl.createNew().withName("b").withType(PsiTypes.booleanType())
    }, false);
  }

  public void testVarargMethodToNonVarag() {
    doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0).withName("i").withType(PsiTypes.intType()),
      ParameterInfoImpl.createNew().withName("b").withType(PsiTypes.booleanType())
    }, false);
  }

  public void testParamJavadoc() {
    doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.create(1).withName("z").withType(PsiTypes.intType()),
      ParameterInfoImpl.create(0).withName("y").withType(PsiTypes.intType())
    }, false);
  }

  public void testParamJavadoc0() {
    doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.create(1).withName("z").withType(PsiTypes.intType()),
      ParameterInfoImpl.create(0).withName("y").withType(PsiTypes.intType())
    }, false);
  }

  public void testParamJavadoc1() {
    doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0).withName("z").withType(PsiTypes.booleanType())
    }, false);
  }

  public void testParamJavadoc2() {
    doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.createNew().withName("z").withType(PsiTypes.booleanType()),
      ParameterInfoImpl.create(0).withName("a").withType(PsiTypes.booleanType()),
    }, false);
  }

  public void testParamJavadoc3() {
    doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0).withName("a").withType(PsiTypes.booleanType()),
      ParameterInfoImpl.createNew().withName("b").withType(PsiTypes.booleanType()),
    }, false);
  }

  public void testParamJavadoc4() {
    doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.createNew().withName("a").withType(PsiTypes.booleanType()),
    }, false);
  }

  public void testParamJavadocRenamedReordered() {
    doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0).withName("a").withType(PsiTypes.booleanType()),
      ParameterInfoImpl.createNew().withName("c").withType(PsiTypes.booleanType()),
      ParameterInfoImpl.create(1).withName("b1").withType(PsiTypes.booleanType()),
    }, false);
  }

  public void testReturnJavadocAdded() {
    doTest("int", new ParameterInfoImpl[0], false);
  }

  public void testReturnJavadocUnchanged() {
    doTest("int", new ParameterInfoImpl[0], false);
  }

  public void testJavadocNoNewLineInserted() {
    doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0).withName("newArgs").withType(PsiTypes.doubleType()),
    }, false);
  }

  public void testSuperCallFromOtherMethod() {
    doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.createNew().withName("nnn").withType(PsiTypes.intType()).withDefaultValue("-222"),
    }, false);
  }

  public void testUseAnyVariable() {
    doTest(null, null, null, method -> new ParameterInfoImpl[]{
      ParameterInfoImpl.createNew().withName("l").withType(myFactory.createTypeFromText("List", method))
        .withDefaultValue("null").useAnySingleVariable()
    }, false);
  }

  public void testUseThisAsAnyVariable() {
    doTest(null, null, null, method -> new ParameterInfoImpl[]{
      ParameterInfoImpl.createNew().withName("l").withType(myFactory.createTypeFromText("List", method))
        .withDefaultValue("null").useAnySingleVariable()
    }, false);
  }

  public void testUseAnyVariableAndDefault() {
    doTest(null, null, null, method -> new ParameterInfoImpl[]{
      ParameterInfoImpl.createNew().withName("c").withType(myFactory.createTypeFromText("C", method))
        .withDefaultValue("null").useAnySingleVariable()
    }, false);
  }

  public void testRemoveVarargParameter() {
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(() ->
                                                                              doTest(null, null, null,
                                                                                     new ParameterInfoImpl[]{ParameterInfoImpl.create(0)},
                                                                                     new ThrownExceptionInfo[0], false)
    );
  }

  public void testEnumConstructor() {
    doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.createNew().withName("i").withType(PsiTypes.intType()).withDefaultValue("10")
    }, false);
  }

  public void testVarargs1() {
    doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.createNew().withName("b").withType(PsiTypes.booleanType()).withDefaultValue("true"),
      ParameterInfoImpl.create(0)
    }, false);
  }

  public void testVarargs2() {
    doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.create(1).withName("i").withType(PsiTypes.intType()),
      ParameterInfoImpl.create(0).withName("b").withType(new PsiEllipsisType(PsiTypes.booleanType()))
    }, false);
  }

  public void testJavadocOfDeleted() {
    doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0).withName("role").withType(PsiTypes.intType()),
    }, false);
  }

  public void testCovariantReturnType() {
    doTest(CommonClassNames.JAVA_LANG_RUNNABLE, new ParameterInfoImpl[0], false);
  }

  public void testReorderExceptions() {
    doTest(null, null, null, new SimpleParameterGen(new ParameterInfoImpl[0]),
           new SimpleExceptionsGen(new ThrownExceptionInfo[]{new JavaThrownExceptionInfo(1), new JavaThrownExceptionInfo(0)}), false);
  }

  public void testAlreadyHandled() {
    doTest(null, null, null, new SimpleParameterGen(new ParameterInfoImpl[0]),
           method -> new ThrownExceptionInfo[]{
             new JavaThrownExceptionInfo(-1, myFactory.createTypeByFQClassName("java.lang.Exception", method.getResolveScope()))
           },
           false
    );
  }

  public void testConstructorException() {
    doTest(null, null, null, new SimpleParameterGen(new ParameterInfoImpl[0]),
           method -> new ThrownExceptionInfo[]{
             new JavaThrownExceptionInfo(-1, myFactory.createTypeByFQClassName("java.io.IOException", method.getResolveScope()))
           },
           false
    );
  }

  public void testAddRuntimeException() {
    doTest(null, null, null, new SimpleParameterGen(new ParameterInfoImpl[0]),
           method -> new ThrownExceptionInfo[]{
             new JavaThrownExceptionInfo(-1, myFactory.createTypeByFQClassName("java.lang.RuntimeException", method.getResolveScope()))
           },
           false
    );
  }

  public void testAddException() {
    doTest(null, null, null, new SimpleParameterGen(new ParameterInfoImpl[0]),
           method -> new ThrownExceptionInfo[]{
             new JavaThrownExceptionInfo(-1, myFactory.createTypeByFQClassName("java.lang.Exception", method.getResolveScope()))
           },
           false
    );
  }

  public void testLessSpecificException() {
    doTest(null, null, null, new SimpleParameterGen(new ParameterInfoImpl[0]),
           method -> new ThrownExceptionInfo[]{
             new JavaThrownExceptionInfo(0, myFactory.createTypeByFQClassName("java.lang.Exception", method.getResolveScope()))
           },
           false
    );
  }

  public void testReorderWithVarargs() {  // IDEADEV-26977
    doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.create(1),
      ParameterInfoImpl.create(0).withName("s").withType(myFactory.createTypeFromText("java.lang.String...", getFile()))
    }, false);
  }

  public void testReorderWithVarargsFromSimpleType() {
    doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.create(1),
      ParameterInfoImpl.create(0).withName("s").withType(myFactory.createTypeFromText("java.lang.String...", getFile()))
    }, false);
  }

  public void testIntroduceParameterWithDefaultValueInHierarchy() {
    doTest(null, new ParameterInfoImpl[]{ParameterInfoImpl.createNew().withName("i").withType(PsiTypes.intType()).withDefaultValue("0")},
           false);
  }

  public void testReorderMultilineMethodParameters() {
    // Inspired by IDEA-54902
    doTest(null, new ParameterInfoImpl[]{ParameterInfoImpl.create(1), ParameterInfoImpl.create(0)}, false);
  }

  public void testRemoveFirstParameter() {
    doTest(null, new ParameterInfoImpl[]{ParameterInfoImpl.create(1)}, false);
  }

  public void testReplaceVarargWithArray() {
    doTest(null, null, null, method -> new ParameterInfoImpl[]{
      ParameterInfoImpl.create(1).withName("l")
        .withType(myFactory.createTypeFromText("List<T>[]", method.getParameterList())).withDefaultValue("null"),
      ParameterInfoImpl.create(0).withName("s").withType(myFactory.createTypeFromText("String", method.getParameterList()))
    }, false);
  }

  public void testReplaceOldStyleArrayWithVarargs() {
    doTest(null, new ParameterInfoImpl[]{ParameterInfoImpl.create(0).withName("a").withType(new PsiEllipsisType(PsiTypes.intType()))},
           false);
  }

  public void testReorderParamsOfFunctionalInterface() {
    doTest(null, null, null, method -> new ParameterInfoImpl[]{
      ParameterInfoImpl.create(1).withName("b").withType(PsiTypes.intType()),
      ParameterInfoImpl.create(0).withName("a").withType(PsiTypes.booleanType())
    }, false);
  }

  public void testReorderParamsOfFunctionalInterfaceExpandMethodReference() {
    GenParams genParams = method -> new ParameterInfoImpl[]{
      ParameterInfoImpl.create(1).withName("b").withType(PsiTypes.intType()),
      ParameterInfoImpl.create(0).withName("a").withType(PsiTypes.intType())
    };
    doTest(null, null, null, genParams, new SimpleExceptionsGen(), false, true);
  }

  public void testAddParenthesisForLambdaParameterList() {
    GenParams genParams = method -> new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0).withName("a").withType(PsiTypes.intType()),
      ParameterInfoImpl.createNew().withName("b").withType(PsiTypes.intType())
    };
    doTest(null, null, null, genParams, new SimpleExceptionsGen(), false, true);
  }

  public void testAddParenthesisForLambdaParameterListDeleteTheOnlyOne() {
    GenParams genParams = method -> new ParameterInfoImpl[0];
    doTest(null, null, null, genParams, new SimpleExceptionsGen(), false, true);
  }

  public void testExpandMethodReferenceToDeleteParameter() {
    GenParams genParams = method -> new ParameterInfoImpl[0];
    doTest(null, null, null, genParams, new SimpleExceptionsGen(), false, true);
  }

  public void testRenameMethodUsedInMethodReference() {
    GenParams genParams = method -> new ParameterInfoImpl[]{ParameterInfoImpl.create(0).withName("a").withType(PsiTypes.intType())};
    doTest(PsiModifier.PRIVATE, "alwaysFalse", null, genParams, new SimpleExceptionsGen(), false, false);
  }

  public void testMethodParametersAlignmentAfterMethodNameChange() {
    getCommonSettings().ALIGN_MULTILINE_PARAMETERS = true;
    getCommonSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    doTest(null, "test123asd", null, new SimpleParameterGen(), new SimpleExceptionsGen(), false);
  }

  public void testMethodParametersAlignmentAfterMethodVisibilityChange() {
    getCommonSettings().ALIGN_MULTILINE_PARAMETERS = true;
    getCommonSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    doTest(PsiModifier.PROTECTED, null, null, new SimpleParameterGen(), new SimpleExceptionsGen(), false);
  }

  public void testMethodParametersAlignmentAfterMethodReturnTypeChange() {
    getCommonSettings().ALIGN_MULTILINE_PARAMETERS = true;
    getCommonSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    doTest(null, null, "Exception", new SimpleParameterGen(), new SimpleExceptionsGen(), false);
  }

  public void testRemoveOverride() {
    doTest(null, null, null, new ParameterInfoImpl[0], new ThrownExceptionInfo[0], false);
  }

  public void testPreserveOverride() {
    doTest(null, null, null, new ParameterInfoImpl[0], new ThrownExceptionInfo[0], false);
  }

  public void testKeepTryWithResources() {
    doTest(null, null, null, new ParameterInfoImpl[0], new ThrownExceptionInfo[0], false);
  }

  public void testVisibilityOfOverriddenMethod() {
    doTest(PsiModifier.PACKAGE_LOCAL, "foo", "void", new ParameterInfoImpl[0], new ThrownExceptionInfo[0], false);
  }

  public void testRemoveExceptions() {
    doTest(null, null, "void", new SimpleParameterGen(), new SimpleExceptionsGen(), false);
  }

  public void testPropagateParameter() {
    String basePath = getRelativePath() + getTestName(false);
    configureByFile(basePath + ".java");
    final PsiElement targetElement = TargetElementUtil.findTargetElement(getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED);
    assertTrue("<caret> is not on method name", targetElement instanceof PsiMethod);
    PsiMethod method = (PsiMethod)targetElement;
    final PsiClass containingClass = method.getContainingClass();
    assertNotNull(containingClass);
    final PsiMethod[] callers = containingClass.findMethodsByName("caller", false);
    assertTrue(callers.length > 0);
    final PsiMethod caller = callers[0];
    final HashSet<PsiMethod> propagateParametersMethods = new HashSet<>();
    propagateParametersMethods.add(caller);
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    new ChangeSignatureProcessor(getProject(), method, false, null, method.getName(),
                                 CanonicalTypes.createTypeWrapper(PsiTypes.voidType()), new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0).withName(parameters[0].getName()).withType(parameters[0].getType()),
      ParameterInfoImpl.createNew().withName("b").withType(PsiTypes.booleanType())}, null, propagateParametersMethods, null
    ).run();
    checkResultByFile(basePath + "_after.java");
  }

  public void testPropagateParameterWithOverrider() {
    String basePath = getRelativePath() + getTestName(false);
    configureByFile(basePath + ".java");
    final PsiElement targetElement = TargetElementUtil.findTargetElement(getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED);
    assertTrue("<caret> is not on method name", targetElement instanceof PsiMethod);
    PsiMethod method = (PsiMethod)targetElement;
    final PsiClass containingClass = method.getContainingClass();
    assertNotNull(containingClass);
    final PsiMethod[] callers = containingClass.findMethodsByName("caller", false);
    assertTrue(callers.length > 0);
    final PsiMethod caller = callers[0];
    final HashSet<PsiMethod> propagateParametersMethods = new HashSet<>();
    propagateParametersMethods.add(caller);
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    new ChangeSignatureProcessor(getProject(), method, false, null, method.getName(),
                                 CanonicalTypes.createTypeWrapper(PsiTypes.voidType()), new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0).withName(parameters[0].getName()).withType(parameters[0].getType()),
      ParameterInfoImpl.createNew().withName("b").withType(PsiTypes.booleanType()).withDefaultValue("true")}, null,
                                 propagateParametersMethods, null
    ).run();
    checkResultByFile(basePath + "_after.java");
  }

  public void testTypeAnnotationsAllAround() {
    //String[] ps = {"@TA(1) int @TA(2) []", "java.util.@TA(4) List<@TA(5) Class<@TA(6) ?>>", "@TA(7) String @TA(8) ..."};
    //String[] ex = {"@TA(42) IllegalArgumentException", "java.lang.@TA(43) IllegalStateException"};
    //doTest("java.util.@TA(0) List<@TA(1) C.@TA(1) Inner>", ps, ex, false);
    String[] ps = {"@TA(2) int @TA(3) []", "@TA(4) List<@TA(5) Class<@TA(6) ?>>", "@TA(7) String @TA(8) ..."};
    String[] ex = ArrayUtil.EMPTY_STRING_ARRAY;
    doTest("@TA(0) List<@TA(1) Inner>", ps, ex, false);
  }

  public void testContractUpdate() {
    GenParams genParams = method -> {
      PsiClassType stringType = PsiType.getJavaLangString(method.getManager(), method.getResolveScope());
      return new ParameterInfoImpl[]{
        ParameterInfoImpl.create(2).withName("a").withType(stringType),
        ParameterInfoImpl.create(0).withName("b").withType(stringType),
        ParameterInfoImpl.createNew().withName("c").withType(stringType)
      };
    };
    doTest(null, null, null, genParams, new SimpleExceptionsGen(), false, false);
  }

  public void testRecordHeaderDeleteRename() {
    doTest(null, null, null, method -> {
      return new ParameterInfoImpl[]{
        ParameterInfoImpl.create(1).withName("yyy").withType(PsiTypes.longType())
      };
    }, false);
  }

  public void testRecordHeaderDeleteRename2() {
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
    final PsiType pointType = facade.getElementFactory().createTypeFromText("Point", null);
    doTest(null, null, null, method -> {
      return new ParameterInfoImpl[]{
        ParameterInfoImpl.create(1).withName("p2").withType(pointType)
      };
    }, false);
  }

  public void testRecordCanonicalConstructorRename() {
    doTest(null, null, null, method -> {
      return new ParameterInfoImpl[]{
        ParameterInfoImpl.create(0).withName("y").withType(PsiTypes.intType()),
        ParameterInfoImpl.create(1).withName("z").withType(PsiTypes.intType()),
        ParameterInfoImpl.create(2).withName("x").withType(PsiTypes.intType())
      };
    }, false);
  }

  public void testRecordCanonicalConstructorRename2() {
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
    final PsiType pointType = facade.getElementFactory().createTypeFromText("Point", null);
    doTest(null, null, null, method -> {
      return new ParameterInfoImpl[]{
        ParameterInfoImpl.create(0).withName("point2").withType(pointType),
        ParameterInfoImpl.create(1).withName("point1").withType(pointType),
        ParameterInfoImpl.create(2).withName("i").withType(PsiTypes.intType())
      };
    }, false);
  }

  public void testRecordCanonicalConstructorReorder() {
    doTest(null, null, null, method -> {
      return new ParameterInfoImpl[]{
        ParameterInfoImpl.create(1).withName("y").withType(PsiTypes.intType()),
        ParameterInfoImpl.create(2).withName("z").withType(PsiTypes.intType()),
        ParameterInfoImpl.create(0).withName("x").withType(PsiTypes.intType())
      };
    }, false);
  }

  public void testRecordCanonicalConstructorReorder2() {
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
    final PsiType pointType = facade.getElementFactory().createTypeFromText("Point", null);
    doTest(null, null, null, method -> {
      return new ParameterInfoImpl[]{
        ParameterInfoImpl.create(2).withName("i").withType(PsiTypes.intType()),
        ParameterInfoImpl.create(1).withName("point2").withType(pointType),
        ParameterInfoImpl.create(0).withName("point1").withType(pointType),
      };
    }, false);
  }

  public void testRecordCanonicalConstructorAddParameter() {
    doTest(null, null, null, method -> {
      return new ParameterInfoImpl[]{
        ParameterInfoImpl.create(0).withName("x").withType(PsiTypes.intType()),
        ParameterInfoImpl.create(-1).withName("y").withType(PsiTypes.intType())
      };
    }, false);
  }

  public void testRecordCanonicalConstructorAddParameter2() {
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
    final PsiType pointType = facade.getElementFactory().createTypeFromText("Point", null);
    doTest(null, null, null, method -> {
      return new ParameterInfoImpl[]{
        ParameterInfoImpl.create(0).withName("point1").withType(pointType),
        ParameterInfoImpl.create(1).withName("point2").withType(pointType),
        ParameterInfoImpl.create(-1).withName("i").withType(PsiTypes.intType())
      };
    }, false);
  }

  public void testRecordComponentWithImportToBeAdded() {
    doTest(null, null, null, method -> {
      return new ParameterInfoImpl[]{
        ParameterInfoImpl.create(-1).withName("y").withType(myFactory.createTypeFromText("java.util.List<java.lang.String>", method))
      };
    }, false);
  }

  public void testRecordCanonicalConstructorMissingHeader() {
    doTest(null, null, null, method -> {
      return new ParameterInfoImpl[]{
        ParameterInfoImpl.create(-1).withName("x").withType(PsiTypes.intType())
      };
    }, false);
  }

  public void testRemoveAnnotation() {
    doTest(null, null, null, method -> new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0).withName("x").withType(PsiTypes.intType())
    }, false);
  }

  public void testAddReturnAnnotation() {
    doTest(null, null, "@org.jetbrains.annotations.NotNull java.lang.String", method -> new ParameterInfoImpl[0], false);
  }

  public void testMultilineJavadoc() { // IDEA-281568
    doTest(null, null, null, method -> new ParameterInfoImpl[]{
      ParameterInfoImpl.create(1).withType(PsiTypes.intType()).withName("b"),
      ParameterInfoImpl.create(0).withType(PsiTypes.intType()).withName("a"),
      ParameterInfoImpl.create(2).withType(PsiTypes.intType()).withName("c"),
    }, false);
  }

  public void testPreserveEmptyTrailingLeadingLinesJavadoc() {
    doTest(null, null, null, method -> new ParameterInfoImpl[]{
      ParameterInfoImpl.create(1).withType(PsiTypes.intType()).withName("b"),
      ParameterInfoImpl.create(0).withType(PsiTypes.intType()).withName("a"),
      ParameterInfoImpl.create(2).withType(PsiTypes.intType()).withName("c"),
    }, false);
  }

  public void testMultilineJavadocWithoutFormatting() { // IDEA-281568
    JavaCodeStyleSettings.getInstance(getProject()).ENABLE_JAVADOC_FORMATTING = false;
    doTest(null, null, null, method -> new ParameterInfoImpl[]{
      ParameterInfoImpl.create(1).withType(PsiTypes.intType()).withName("b"),
      ParameterInfoImpl.create(0).withType(PsiTypes.intType()).withName("a"),
      ParameterInfoImpl.create(2).withType(PsiTypes.intType()).withName("c"),
    }, false);
  }

  public void testJavadocNotBrokenAfterDelete() { // IDEA-139879
    doTest(null, null, null, method -> new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0).withType(PsiTypes.intType()).withName("i1")
    }, false);
  }

  public void testNoGapsInParameterTags() { // IDEA-139879
    doTest(null, null, null, method -> new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0).withType(PsiTypes.intType()).withName("b"),
      ParameterInfoImpl.create(1).withType(PsiTypes.longType()).withName("a"),
      ParameterInfoImpl.create(2).withType(PsiTypes.booleanType()).withName("c"),
      ParameterInfoImpl.createNew().withType(PsiTypes.shortType()).withName("d"),
    }, false);
  }

  public void testVarargToArray() { // IDEA-318626
    doTest(null, null, null, method -> new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0).withName("x"),
      ParameterInfoImpl.create(1).withName("args").withType(
        method.getParameterList().getParameter(1).getType().getDeepComponentType().createArrayType())
    }, false);
  }

  public void testEnumVarargToArray() {
    doTest(null, null, null, method -> new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0).withName("x"),
      ParameterInfoImpl.create(1).withName("args").withType(
        method.getParameterList().getParameter(1).getType().getDeepComponentType().createArrayType())
    }, false);
  }

  public void testDefCtorVarargToArray() {
    doTest(null, null, null, method -> new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0).withName("args").withType(
        method.getParameterList().getParameter(0).getType().getDeepComponentType().createArrayType())
    }, false);
  }

  public void testArrayToVararg() { // IDEA-318626
    doTest(null, null, null, method -> new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0).withName("x"),
      ParameterInfoImpl.create(1).withName("args").withType(
        new PsiEllipsisType(method.getParameterList().getParameter(1).getType().getDeepComponentType()))
    }, false);
  }

  public void testVarargToArrayReorder() { // IDEA-318626
    doTest(null, null, null, method -> new ParameterInfoImpl[]{
      ParameterInfoImpl.create(1).withName("args").withType(
        method.getParameterList().getParameter(1).getType().getDeepComponentType().createArrayType()),
      ParameterInfoImpl.create(0).withName("x")
    }, false);
  }

  /* Markdown javadoc variant */
  public void testParamJavadocMarkdown2() {
    doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.createNew().withName("z").withType(PsiTypes.booleanType()),
      ParameterInfoImpl.create(0).withName("a").withType(PsiTypes.booleanType()),
    }, false);
  }

  public void testRecordHeaderDeleteRenameMarkdown() {
    doTest(null, null, null, method -> {
      return new ParameterInfoImpl[]{
        ParameterInfoImpl.create(1).withName("yyy").withType(PsiTypes.longType())
      };
    }, false);
  }

  public void testRecordCanonicalConstructorReorderMarkdown2() {
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
    final PsiType pointType = facade.getElementFactory().createTypeFromText("Point", null);
    doTest(null, null, null, method -> {
      return new ParameterInfoImpl[]{
        ParameterInfoImpl.create(2).withName("i").withType(PsiTypes.intType()),
        ParameterInfoImpl.create(1).withName("point2").withType(pointType),
        ParameterInfoImpl.create(0).withName("point1").withType(pointType),
      };
    }, false);
  }

  public void testJavadocMarkdownOfDeleted() {
    doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0).withName("role").withType(PsiTypes.intType()),
    }, false);
  }

  public void testRecordCanonicalConstructorAddParameterMarkdown2() {
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
    final PsiType pointType = facade.getElementFactory().createTypeFromText("Point", null);
    doTest(null, null, null, method -> {
      return new ParameterInfoImpl[]{
        ParameterInfoImpl.create(0).withName("point1").withType(pointType),
        ParameterInfoImpl.create(1).withName("point2").withType(pointType),
        ParameterInfoImpl.create(-1).withName("i").withType(PsiTypes.intType())
      };
    }, false);
  }

  public void testJavadocMarkdownNoNewLineInserted() {
    doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0).withName("newArgs").withType(PsiTypes.doubleType()),
    }, false);
  }

  public void testMultilineJavadocMarkdownWithoutFormatting() { // IDEA-281568
    JavaCodeStyleSettings.getInstance(getProject()).ENABLE_JAVADOC_FORMATTING = false;
    doTest(null, null, null, method -> new ParameterInfoImpl[]{
      ParameterInfoImpl.create(1).withType(PsiTypes.intType()).withName("b"),
      ParameterInfoImpl.create(0).withType(PsiTypes.intType()).withName("a"),
      ParameterInfoImpl.create(2).withType(PsiTypes.intType()).withName("c"),
    }, false);
  }

  public void testParamJavadocMarkdown1() {
    doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0).withName("z").withType(PsiTypes.booleanType())
    }, false);
  }

  public void testParamJavadocMarkdown0() {
    doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.create(1).withName("z").withType(PsiTypes.intType()),
      ParameterInfoImpl.create(0).withName("y").withType(PsiTypes.intType())
    }, false);
  }

  public void testReturnJavadocMarkdownAdded() {
    doTest("int", new ParameterInfoImpl[0], false);
  }

  public void testRecordCanonicalConstructorRenameMarkdown() {
    doTest(null, null, null, method -> {
      return new ParameterInfoImpl[]{
        ParameterInfoImpl.create(0).withName("y").withType(PsiTypes.intType()),
        ParameterInfoImpl.create(1).withName("z").withType(PsiTypes.intType()),
        ParameterInfoImpl.create(2).withName("x").withType(PsiTypes.intType())
      };
    }, false);
  }

  public void testParamJavadocMarkdown() {
    doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.create(1).withName("z").withType(PsiTypes.intType()),
      ParameterInfoImpl.create(0).withName("y").withType(PsiTypes.intType())
    }, false);
  }

  public void testRecordCanonicalConstructorAddParameterMarkdown() {
    doTest(null, null, null, method -> {
      return new ParameterInfoImpl[]{
        ParameterInfoImpl.create(0).withName("x").withType(PsiTypes.intType()),
        ParameterInfoImpl.create(-1).withName("y").withType(PsiTypes.intType())
      };
    }, false);
  }

  public void testParamJavadocMarkdown4() {
    doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.createNew().withName("a").withType(PsiTypes.booleanType()),
    }, false);
  }

  public void testSCRMarkdown40895() {
    doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0).withName("y").withType(PsiTypes.intType()),
      ParameterInfoImpl.create(1).withName("b").withType(PsiTypes.booleanType())
    }, false);
  }

  public void testReturnJavadocMarkdownUnchanged() {
    doTest("int", new ParameterInfoImpl[0], false);
  }

  public void testRecordComponentWithImportToBeAddedMarkdown() {
    doTest(null, null, null, method -> {
      return new ParameterInfoImpl[]{
        ParameterInfoImpl.create(-1).withName("y").withType(myFactory.createTypeFromText("java.util.List<java.lang.String>", method))
      };
    }, false);
  }

  public void testMethodParametersAlignmentAfterMethodReturnTypeChangeMarkdown() {
    getCommonSettings().ALIGN_MULTILINE_PARAMETERS = true;
    getCommonSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    doTest(null, null, "Exception", new SimpleParameterGen(), new SimpleExceptionsGen(), false);
  }

  public void testParamJavadocMarkdown3() {
    doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0).withName("a").withType(PsiTypes.booleanType()),
      ParameterInfoImpl.createNew().withName("b").withType(PsiTypes.booleanType()),
    }, false);
  }

  public void testRecordHeaderDeleteRenameMarkdown2() {
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
    final PsiType pointType = facade.getElementFactory().createTypeFromText("Point", null);
    doTest(null, null, null, method -> {
      return new ParameterInfoImpl[]{
        ParameterInfoImpl.create(1).withName("p2").withType(pointType)
      };
    }, false);
  }

  public void testParamJavadocMarkdownRenamedReordered() {
    doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0).withName("a").withType(PsiTypes.booleanType()),
      ParameterInfoImpl.createNew().withName("c").withType(PsiTypes.booleanType()),
      ParameterInfoImpl.create(1).withName("b1").withType(PsiTypes.booleanType()),
    }, false);
  }

  public void testJavadocMarkdownGenericsLink() {
    doTest(null, new ParameterInfoImpl[]{
      ParameterInfoImpl.createNew().withName("y").withType(myFactory.createTypeFromText("java.util.List<java.lang.String>", null)),
      ParameterInfoImpl.create(0).withName("a").withType(PsiTypes.booleanType())
    }, false);
  }

  public void testRecordCanonicalConstructorReorderMarkdown() {
    doTest(null, null, null, method -> {
      return new ParameterInfoImpl[]{
        ParameterInfoImpl.create(1).withName("y").withType(PsiTypes.intType()),
        ParameterInfoImpl.create(2).withName("z").withType(PsiTypes.intType()),
        ParameterInfoImpl.create(0).withName("x").withType(PsiTypes.intType())
      };
    }, false);
  }

  public void testMultilineJavadocMarkdown() { // IDEA-281568
    doTest(null, null, null, method -> new ParameterInfoImpl[]{
      ParameterInfoImpl.create(1).withType(PsiTypes.intType()).withName("b"),
      ParameterInfoImpl.create(0).withType(PsiTypes.intType()).withName("a"),
      ParameterInfoImpl.create(2).withType(PsiTypes.intType()).withName("c"),
    }, false);
  }

  public void testPreserveEmptyTrailingLeadingLinesJavadocMarkdown() {
      doTest(null, null, null, method -> new ParameterInfoImpl[]{
        ParameterInfoImpl.create(1).withType(PsiTypes.intType()).withName("b"),
        ParameterInfoImpl.create(0).withType(PsiTypes.intType()).withName("a"),
        ParameterInfoImpl.create(2).withType(PsiTypes.intType()).withName("c"),
      }, false);
  }

  public void testNoGapsInParameterTagsMarkdown() { // IDEA-139879
    doTest(null, null, null, method -> new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0).withType(PsiTypes.intType()).withName("b"),
      ParameterInfoImpl.create(1).withType(PsiTypes.longType()).withName("a"),
      ParameterInfoImpl.create(2).withType(PsiTypes.booleanType()).withName("c"),
      ParameterInfoImpl.createNew().withType(PsiTypes.shortType()).withName("d"),
    }, false);
  }

  public void testRecordCanonicalConstructorRenameMarkdown2() {
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
    final PsiType pointType = facade.getElementFactory().createTypeFromText("Point", null);
    doTest(null, null, null, method -> {
      return new ParameterInfoImpl[]{
        ParameterInfoImpl.create(0).withName("point2").withType(pointType),
        ParameterInfoImpl.create(1).withName("point1").withType(pointType),
        ParameterInfoImpl.create(2).withName("i").withType(PsiTypes.intType())
      };
    }, false);
  }

  public void testJavadocMarkdownNotBrokenAfterDelete() { // IDEA-139879
    doTest(null, null, null, method -> new ParameterInfoImpl[]{
      ParameterInfoImpl.create(0).withType(PsiTypes.intType()).withName("i1")
    }, false);
  }

  public void testConflictsForFieldRecord() {
    assertConflict(() -> {
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
      final PsiType pointType = facade.getElementFactory().createTypeFromText("String", null);
      doTest(null, null, null, method -> {
        return new ParameterInfoImpl[]{
          ParameterInfoImpl.create(1).withName("b").withType(pointType)
        };
      }, false);
    }, "Record component 'a' is used");
  }

  public void testConflictsForGetRecord() {
    assertConflict(() -> {
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
      final PsiType pointType = facade.getElementFactory().createTypeFromText("String", null);
      doTest(null, null, null, method -> {
        return new ParameterInfoImpl[]{
          ParameterInfoImpl.create(1).withName("b").withType(pointType)
        };
      }, false);
    }, "Record component 'a' is used");
  }

  public void testWithoutConflictsForGet() {
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
    final PsiType pointType = facade.getElementFactory().createTypeFromText("String", null);
    doTest(null, null, null, method -> {
      return new ParameterInfoImpl[]{
        ParameterInfoImpl.create(1).withName("b").withType(pointType)
      };
    }, false);
  }

  public void testConflictsSwitchUsedDeconstruction() {
    assertConflict(() -> {
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
      final PsiType pointType = facade.getElementFactory().createTypeFromText("String", null);
      doTest(null, null, null, method -> {
        return new ParameterInfoImpl[]{
          ParameterInfoImpl.create(1).withName("b").withType(pointType)
        };
      }, false);
    }, "Record component 'a' is used");
  }

  public void testWithoutConflictsSwitchUsedDeconstruction() {
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
    final PsiType pointType = facade.getElementFactory().createTypeFromText("int", null);
    doTest(null, null, null, method -> {
      return new ParameterInfoImpl[]{
        ParameterInfoImpl.create(0).withName("a").withType(pointType)
      };
    }, false);
  }

  public void testConflictsSwitchNarrowedDeconstruction() {
    assertConflict(() -> {
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
      final PsiType pointType = facade.getElementFactory().createTypeFromText("int", null);
      doTest(null, null, null, method -> {
        return new ParameterInfoImpl[]{
          ParameterInfoImpl.create(0).withName("a").withType(pointType)
        };
      }, false);
    }, "Record component 'b' is used");
  }

  public void testWithoutConflictsSwitchExtendedDeconstruction() {
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
    final PsiType pointType = facade.getElementFactory().createTypeFromText("int", null);
    doTest(null, null, null, method -> {
      return new ParameterInfoImpl[]{
        ParameterInfoImpl.create(0).withName("a").withType(pointType)
      };
    }, false);
  }

  public void testConflictsSwitchNestedDeconstruction() {
    assertConflict(() -> {
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
      final PsiType pointType = facade.getElementFactory().createTypeFromText("int", null);
      doTest(null, null, null, method -> {
        return new ParameterInfoImpl[]{
          ParameterInfoImpl.create(0).withName("x").withType(pointType)
        };
      }, false);
    }, "Record component 'y' is used");
  }

  public void testWithoutConflictsSwitchNestedDeconstruction() {
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
    final PsiType pointType = facade.getElementFactory().createTypeFromText("int", null);
    doTest(null, null, null, method -> {
      return new ParameterInfoImpl[]{
        ParameterInfoImpl.create(0).withName("x").withType(pointType)
      };
    }, false);
  }

  public void testConflictsSwitchNestedDeconstructionDifTypes() {
    assertConflict(() -> {
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
      final PsiType pointType = facade.getElementFactory().createTypeFromText("int", null);
      doTest(null, null, null, method -> {
        return new ParameterInfoImpl[]{
          ParameterInfoImpl.create(0).withName("x").withType(pointType)
        };
      }, false);
    }, "Record component 'x' is used");
  }

  @SuppressWarnings("CatchMayIgnoreException")
  private static void assertConflict(@NotNull Runnable runnable,
                                     @NotNull String expectedMessage) {
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
