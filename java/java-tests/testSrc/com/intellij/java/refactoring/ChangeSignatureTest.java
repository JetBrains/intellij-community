/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.refactoring;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.JavaThrownExceptionInfo;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.refactoring.changeSignature.ThrownExceptionInfo;
import com.intellij.refactoring.util.CanonicalTypes;

import java.util.HashSet;

/**
 * @author dsl
 */
public class ChangeSignatureTest extends ChangeSignatureBaseTest {

  private CommonCodeStyleSettings getJavaSettings() {
    return getCurrentCodeStyleSettings().getCommonSettings(JavaLanguage.INSTANCE);
  }

  public void testSimple() {
    doTest(null, null, null, new ParameterInfoImpl[0], new ThrownExceptionInfo[0], false);
  }

  public void testParameterReorder() {
    doTest(null, new ParameterInfoImpl[]{new ParameterInfoImpl(1), new ParameterInfoImpl(0)}, false);
  }

  public void testWarnAboutContract() {
    try {
      doTest(null, new ParameterInfoImpl[]{new ParameterInfoImpl(1), new ParameterInfoImpl(0)}, false);
      fail("Conflict expected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException ignored) { }
  }

  public void testWarnAboutAssigningWeakerAccessPrivileges() {
    try {
      doTest(PsiModifier.PRIVATE,null, null, new ParameterInfoImpl[0], new ThrownExceptionInfo[0], false);
      fail("Conflict expected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException ignored) { }
  }

  public void testDelegateWithoutChangesWarnAboutSameMethodInClass() {
    try {
      doTest(null, new ParameterInfoImpl[0], true);
      fail("Conflict expected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException ignored) { }
  }

  public void testDuplicatedSignatureInInheritor() {
    try {
      doTest(null, new ParameterInfoImpl[] {new ParameterInfoImpl(-1, "i", PsiType.INT)}, true);
      fail("Conflict expected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException ignored) { }
  }

  public void testConflictForUsedParametersInMethodBody() {
    try {
      doTest(null, new ParameterInfoImpl[0], true);
      fail("Conflict expected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException ignored) { }
  }

  public void testGenericTypes() {
    doTest(null, null, "T", method -> new ParameterInfoImpl[]{
      new ParameterInfoImpl(-1, "x", myFactory.createTypeFromText("T", method.getParameterList()), "null"),
      new ParameterInfoImpl(-1, "y", myFactory.createTypeFromText("C<T>", method.getParameterList()), "null")
    }, false);
  }

  public void testGenericTypesInOldParameters() {
    doTest(null, null, null, method -> new ParameterInfoImpl[]{
      new ParameterInfoImpl(0, "t", myFactory.createTypeFromText("T", method), null)
    }, false);
  }

  public void testTypeParametersInMethod() {
    doTest(null, null, null, method -> new ParameterInfoImpl[]{
      new ParameterInfoImpl(-1, "t", myFactory.createTypeFromText("T", method.getParameterList()), "null"),
      new ParameterInfoImpl(-1, "u", myFactory.createTypeFromText("U", method.getParameterList()), "null"),
      new ParameterInfoImpl(-1, "cu", myFactory.createTypeFromText("C<U>", method.getParameterList()), "null")
    }, false);
  }

  public void testDefaultConstructor() {
    doTest(null,
           new ParameterInfoImpl[]{
             new ParameterInfoImpl(-1, "j", PsiType.INT, "27")
           }, false
    );
  }

  public void testGenerateDelegate() {
    doTest(null,
           new ParameterInfoImpl[]{
             new ParameterInfoImpl(-1, "i", PsiType.INT, "27")
           }, true
    );
  }

  public void testGenerateDelegateForAbstract() {
    doTest(null,
           new ParameterInfoImpl[]{
             new ParameterInfoImpl(-1, "i", PsiType.INT, "27")
           }, true
    );
  }

  public void testGenerateDelegateWithReturn() {
    doTest(null,
           new ParameterInfoImpl[]{
             new ParameterInfoImpl(-1, "i", PsiType.INT, "27")
           }, true
    );
  }

  public void testGenerateDelegateWithParametersReordering() {
    doTest(null,
           new ParameterInfoImpl[]{
             new ParameterInfoImpl(1),
             new ParameterInfoImpl(-1, "c", PsiType.CHAR, "'a'"),
             new ParameterInfoImpl(0, "j", PsiType.INT)
           }, true
    );
  }

  public void testGenerateDelegateConstructor() {
    doTest(null, new ParameterInfoImpl[0], true);
  }

  public void testGenerateDelegateDefaultConstructor() {
    doTest(null, new ParameterInfoImpl[]{
      new ParameterInfoImpl(-1, "i", PsiType.INT, "27")
    }, true);
  }

  public void testSCR40895() {
    doTest(null, new ParameterInfoImpl[]{
      new ParameterInfoImpl(0, "y", PsiType.INT),
      new ParameterInfoImpl(1, "b", PsiType.BOOLEAN)
    }, false);
  }

  public void testJavadocGenericsLink() {
    doTest(null, new ParameterInfoImpl[]{
      new ParameterInfoImpl(-1, "y", myFactory.createTypeFromText("java.util.List<java.lang.String>", null)),
      new ParameterInfoImpl(0, "a", PsiType.BOOLEAN)
    }, false);
  }

  public void testParamNameSameAsFieldName() {
    doTest(null, new ParameterInfoImpl[]{
      new ParameterInfoImpl(0, "fieldName", PsiType.INT)
    }, false);
  }

  public void testParamNameNoConflict() {
    doTest(null, new ParameterInfoImpl[]{
      new ParameterInfoImpl(0),
      new ParameterInfoImpl(-1, "b", PsiType.BOOLEAN)
    }, false);
  }

  public void testVarargMethodToNonVarag() {
    doTest(null, new ParameterInfoImpl[]{
      new ParameterInfoImpl(0, "i", PsiType.INT),
      new ParameterInfoImpl(-1, "b", PsiType.BOOLEAN)
    }, false);
  }

  public void testParamJavadoc() {
    doTest(null, new ParameterInfoImpl[]{
      new ParameterInfoImpl(1, "z", PsiType.INT),
      new ParameterInfoImpl(0, "y", PsiType.INT)
    }, false);
  }

  public void testParamJavadoc0() {
    doTest(null, new ParameterInfoImpl[]{
      new ParameterInfoImpl(1, "z", PsiType.INT),
      new ParameterInfoImpl(0, "y", PsiType.INT)
    }, false);
  }

  public void testParamJavadoc1() {
    doTest(null, new ParameterInfoImpl[]{
      new ParameterInfoImpl(0, "z", PsiType.BOOLEAN)
    }, false);
  }

  public void testParamJavadoc2() {
    doTest(null, new ParameterInfoImpl[]{
      new ParameterInfoImpl(-1, "z", PsiType.BOOLEAN),
      new ParameterInfoImpl(0, "a", PsiType.BOOLEAN),
    }, false);
  }

  public void testParamJavadoc3() {
    doTest(null, new ParameterInfoImpl[]{
      new ParameterInfoImpl(0, "a", PsiType.BOOLEAN),
      new ParameterInfoImpl(-1, "b", PsiType.BOOLEAN),
    }, false);
  }

  public void testParamJavadocRenamedReordered() {
    doTest(null, new ParameterInfoImpl[]{
      new ParameterInfoImpl(0, "a", PsiType.BOOLEAN),
      new ParameterInfoImpl(-1, "c", PsiType.BOOLEAN),
      new ParameterInfoImpl(1, "b1", PsiType.BOOLEAN),
    }, false);
  }

  public void testJavadocNoNewLineInserted() {
    doTest(null, new ParameterInfoImpl[]{
      new ParameterInfoImpl(0, "newArgs", PsiType.DOUBLE),
    }, false);
  }

  public void testSuperCallFromOtherMethod() {
    doTest(null, new ParameterInfoImpl[]{
      new ParameterInfoImpl(-1, "nnn", PsiType.INT, "-222"),
    }, false);
  }

  public void testUseAnyVariable() {
    doTest(null, null, null, method -> new ParameterInfoImpl[]{
      new ParameterInfoImpl(-1, "l", myFactory.createTypeFromText("List", method), "null", true)
    }, false);
  }

  public void testUseThisAsAnyVariable() {
    doTest(null, null, null, method -> new ParameterInfoImpl[]{
      new ParameterInfoImpl(-1, "l", myFactory.createTypeFromText("List", method), "null", true)
    }, false);
  }

  public void testUseAnyVariableAndDefault() {
    doTest(null, null, null, method -> new ParameterInfoImpl[]{
      new ParameterInfoImpl(-1, "c", myFactory.createTypeFromText("C", method), "null", true)
    }, false);
  }

  public void testRemoveVarargParameter() {
    try {
      BaseRefactoringProcessor.ConflictsInTestsException.setTestIgnore(true);
      doTest(null, null, null, new ParameterInfoImpl[]{new ParameterInfoImpl(0)}, new ThrownExceptionInfo[0], false);
    }
    finally {
      BaseRefactoringProcessor.ConflictsInTestsException.setTestIgnore(false);
    }
  }

  public void testEnumConstructor() {
    doTest(null, new ParameterInfoImpl[]{
      new ParameterInfoImpl(-1, "i", PsiType.INT, "10")
    }, false);
  }

  public void testVarargs1() {
    doTest(null, new ParameterInfoImpl[]{
      new ParameterInfoImpl(-1, "b", PsiType.BOOLEAN, "true"),
      new ParameterInfoImpl(0)
    }, false);
  }

  public void testVarargs2() {
    doTest(null, new ParameterInfoImpl[]{
      new ParameterInfoImpl(1, "i", PsiType.INT),
      new ParameterInfoImpl(0, "b", new PsiEllipsisType(PsiType.BOOLEAN))
    }, false);
  }

  public void testJavadocOfDeleted() {
    doTest(null, new ParameterInfoImpl[]{
      new ParameterInfoImpl(0, "role", PsiType.INT),
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
      new ParameterInfoImpl(1),
      new ParameterInfoImpl(0, "s", myFactory.createTypeFromText("java.lang.String...", getFile()))
    }, false);
  }

  public void testIntroduceParameterWithDefaultValueInHierarchy() {
    doTest(null, new ParameterInfoImpl[]{new ParameterInfoImpl(-1, "i", PsiType.INT, "0")}, false);
  }

  public void testReorderMultilineMethodParameters() {
    // Inspired by IDEA-54902
    doTest(null, new ParameterInfoImpl[]{new ParameterInfoImpl(1), new ParameterInfoImpl(0)}, false);
  }

  public void testRemoveFirstParameter() {
    doTest(null, new ParameterInfoImpl[]{new ParameterInfoImpl(1)}, false);
  }

  public void testReplaceVarargWithArray() {
    doTest(null, null, null, method -> new ParameterInfoImpl[]{
      new ParameterInfoImpl(1, "l", myFactory.createTypeFromText("List<T>[]", method.getParameterList()), "null", false),
      new ParameterInfoImpl(0, "s", myFactory.createTypeFromText("String", method.getParameterList()))
    }, false);
  }

  public void testReplaceOldStyleArrayWithVarargs() {
    doTest(null, new ParameterInfoImpl[] {new ParameterInfoImpl(0, "a", new PsiEllipsisType(PsiType.INT))}, false);
  }

  public void testReorderParamsOfFunctionalInterface() {
    doTest(null, null, null, method -> new ParameterInfoImpl[]{
      new ParameterInfoImpl(1, "b", PsiType.INT),
      new ParameterInfoImpl(0, "a", PsiType.BOOLEAN)
    }, false);
  }

  public void testReorderParamsOfFunctionalInterfaceExpandMethodReference() {
    GenParams genParams = method -> new ParameterInfoImpl[]{
      new ParameterInfoImpl(1, "b", PsiType.INT),
      new ParameterInfoImpl(0, "a", PsiType.INT)
    };
    doTest(null, null, null, genParams, new SimpleExceptionsGen(), false, true);
  }

  public void testExpandMethodReferenceToDeleteParameter() {
    GenParams genParams = method -> new ParameterInfoImpl[0];
    doTest(null, null, null, genParams, new SimpleExceptionsGen(), false, true);
  }

  public void testRenameMethodUsedInMethodReference() {
    GenParams genParams = method -> new ParameterInfoImpl[] {new ParameterInfoImpl(0, "a", PsiType.INT)};
    doTest(PsiModifier.PRIVATE, "alwaysFalse", null, genParams, new SimpleExceptionsGen(), false, false);
  }

  public void testMethodParametersAlignmentAfterMethodNameChange() {
    getJavaSettings().ALIGN_MULTILINE_PARAMETERS = true;
    getJavaSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    doTest(null, "test123asd", null, new SimpleParameterGen(), new SimpleExceptionsGen(), false);
  }

  public void testMethodParametersAlignmentAfterMethodVisibilityChange() {
    getJavaSettings().ALIGN_MULTILINE_PARAMETERS = true;
    getJavaSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    doTest(PsiModifier.PROTECTED, null, null, new SimpleParameterGen(), new SimpleExceptionsGen(), false);
  }

  public void testMethodParametersAlignmentAfterMethodReturnTypeChange() {
    getJavaSettings().ALIGN_MULTILINE_PARAMETERS = true;
    getJavaSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    doTest(null, null, "Exception", new SimpleParameterGen(), new SimpleExceptionsGen(), false);
  }

  public void testRemoveOverride() {
    doTest(null, null, null, new ParameterInfoImpl[0], new ThrownExceptionInfo[0], false);
  }

  public void testPreserveOverride() {
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
    assertTrue(containingClass != null);
    final PsiMethod[] callers = containingClass.findMethodsByName("caller", false);
    assertTrue(callers.length > 0);
    final PsiMethod caller = callers[0];
    final HashSet<PsiMethod> propagateParametersMethods = new HashSet<>();
    propagateParametersMethods.add(caller);
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    new ChangeSignatureProcessor(getProject(), method, false, null, method.getName(),
                                 CanonicalTypes.createTypeWrapper(PsiType.VOID), new ParameterInfoImpl[]{
      new ParameterInfoImpl(0, parameters[0].getName(), parameters[0].getType()),
      new ParameterInfoImpl(-1, "b", PsiType.BOOLEAN)}, null, propagateParametersMethods, null
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
    assertTrue(containingClass != null);
    final PsiMethod[] callers = containingClass.findMethodsByName("caller", false);
    assertTrue(callers.length > 0);
    final PsiMethod caller = callers[0];
    final HashSet<PsiMethod> propagateParametersMethods = new HashSet<>();
    propagateParametersMethods.add(caller);
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    new ChangeSignatureProcessor(getProject(), method, false, null, method.getName(),
                                 CanonicalTypes.createTypeWrapper(PsiType.VOID), new ParameterInfoImpl[]{
      new ParameterInfoImpl(0, parameters[0].getName(), parameters[0].getType()),
      new ParameterInfoImpl(-1, "b", PsiType.BOOLEAN, "true")}, null, propagateParametersMethods, null
    ).run();
    checkResultByFile(basePath + "_after.java");
  }

  public void testTypeAnnotationsAllAround() {
    //String[] ps = {"@TA(1) int @TA(2) []", "java.util.@TA(4) List<@TA(5) Class<@TA(6) ?>>", "@TA(7) String @TA(8) ..."};
    //String[] ex = {"@TA(42) IllegalArgumentException", "java.lang.@TA(43) IllegalStateException"};
    //doTest("java.util.@TA(0) List<@TA(1) C.@TA(1) Inner>", ps, ex, false);
    String[] ps = {"@TA(2) int @TA(3) []", "@TA(4) List<@TA(5) Class<@TA(6) ?>>", "@TA(7) String @TA(8) ..."};
    String[] ex = {};
    doTest("@TA(0) List<@TA(1) Inner>", ps, ex, false);
  }

  /* workers */
}
