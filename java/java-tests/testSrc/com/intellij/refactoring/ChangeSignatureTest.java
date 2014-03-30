/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.psi.*;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.JavaThrownExceptionInfo;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.refactoring.changeSignature.ThrownExceptionInfo;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;

/**
 * @author dsl
 */
public class ChangeSignatureTest extends LightRefactoringTestCase {
  private PsiElementFactory myFactory;

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFactory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
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

  public void testGenericTypes() {
    doTest(null, null, "T", new GenParams() {
      @Override
      public ParameterInfoImpl[] genParams(PsiMethod method) throws IncorrectOperationException {
        return new ParameterInfoImpl[]{
          new ParameterInfoImpl(-1, "x", myFactory.createTypeFromText("T", method.getParameterList()), "null"),
          new ParameterInfoImpl(-1, "y", myFactory.createTypeFromText("C<T>", method.getParameterList()), "null")
        };
      }
    }, false);
  }

  public void testGenericTypesInOldParameters() {
    doTest(null, null, null, new GenParams() {
      @Override
      public ParameterInfoImpl[] genParams(PsiMethod method) throws IncorrectOperationException {
        return new ParameterInfoImpl[]{
          new ParameterInfoImpl(0, "t", myFactory.createTypeFromText("T", method), null)
        };
      }
    }, false);
  }

  public void testTypeParametersInMethod() {
    doTest(null, null, null, new GenParams() {
      @Override
      public ParameterInfoImpl[] genParams(PsiMethod method) throws IncorrectOperationException {
        return new ParameterInfoImpl[]{
          new ParameterInfoImpl(-1, "t", myFactory.createTypeFromText("T", method.getParameterList()), "null"),
          new ParameterInfoImpl(-1, "u", myFactory.createTypeFromText("U", method.getParameterList()), "null"),
          new ParameterInfoImpl(-1, "cu", myFactory.createTypeFromText("C<U>", method.getParameterList()), "null")
        };
      }
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
    doTest(null, null, null, new GenParams() {
      @Override
      public ParameterInfoImpl[] genParams(PsiMethod method) throws IncorrectOperationException {
        return new ParameterInfoImpl[]{
          new ParameterInfoImpl(-1, "l", myFactory.createTypeFromText("List", method), "null", true)
        };
      }
    }, false);
  }

  public void testUseThisAsAnyVariable() {
    doTest(null, null, null, new GenParams() {
      @Override
      public ParameterInfoImpl[] genParams(PsiMethod method) throws IncorrectOperationException {
        return new ParameterInfoImpl[]{
          new ParameterInfoImpl(-1, "l", myFactory.createTypeFromText("List", method), "null", true)
        };
      }
    }, false);
  }

  public void testUseAnyVariableAndDefault() {
    doTest(null, null, null, new GenParams() {
      @Override
      public ParameterInfoImpl[] genParams(PsiMethod method) throws IncorrectOperationException {
        return new ParameterInfoImpl[]{
          new ParameterInfoImpl(-1, "c", myFactory.createTypeFromText("C", method), "null", true)
        };
      }
    }, false);
  }

  public void testRemoveVarargParameter() {
    doTest(null, null, null, new ParameterInfoImpl[]{new ParameterInfoImpl(0)}, new ThrownExceptionInfo[0], false);
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

  public void testCovariantReturnType() {
    doTest(CommonClassNames.JAVA_LANG_RUNNABLE, new ParameterInfoImpl[0], false);
  }

  public void testReorderExceptions() {
    doTest(null, null, null, new SimpleParameterGen(new ParameterInfoImpl[0]),
           new SimpleExceptionsGen(new ThrownExceptionInfo[]{new JavaThrownExceptionInfo(1), new JavaThrownExceptionInfo(0)}), false);
  }

  public void testAlreadyHandled() {
    doTest(null, null, null, new SimpleParameterGen(new ParameterInfoImpl[0]),
           new GenExceptions() {
             @Override
             public ThrownExceptionInfo[] genExceptions(PsiMethod method) {
               return new ThrownExceptionInfo[]{
                 new JavaThrownExceptionInfo(-1, myFactory.createTypeByFQClassName("java.lang.Exception", method.getResolveScope()))
               };
             }
           },
           false
    );
  }

  public void testConstructorException() {
    doTest(null, null, null, new SimpleParameterGen(new ParameterInfoImpl[0]),
           new GenExceptions() {
             @Override
             public ThrownExceptionInfo[] genExceptions(PsiMethod method) {
               return new ThrownExceptionInfo[]{
                 new JavaThrownExceptionInfo(-1, myFactory.createTypeByFQClassName("java.io.IOException", method.getResolveScope()))
               };
             }
           },
           false
    );
  }

  public void testAddRuntimeException() {
    doTest(null, null, null, new SimpleParameterGen(new ParameterInfoImpl[0]),
           new GenExceptions() {
             @Override
             public ThrownExceptionInfo[] genExceptions(PsiMethod method) {
               return new ThrownExceptionInfo[]{
                 new JavaThrownExceptionInfo(-1, myFactory.createTypeByFQClassName("java.lang.RuntimeException", method.getResolveScope()))
               };
             }
           },
           false
    );
  }

  public void testAddException() {
    doTest(null, null, null, new SimpleParameterGen(new ParameterInfoImpl[0]),
           new GenExceptions() {
             @Override
             public ThrownExceptionInfo[] genExceptions(PsiMethod method) {
               return new ThrownExceptionInfo[]{
                 new JavaThrownExceptionInfo(-1, myFactory.createTypeByFQClassName("java.lang.Exception", method.getResolveScope()))
               };
             }
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
    doTest(null, null, null, new GenParams() {
      @Override
      public ParameterInfoImpl[] genParams(PsiMethod method) throws IncorrectOperationException {
        return new ParameterInfoImpl[]{
          new ParameterInfoImpl(1, "l", myFactory.createTypeFromText("List<T>[]", method.getParameterList()), "null", false),
          new ParameterInfoImpl(0, "s", myFactory.createTypeFromText("String", method.getParameterList()))
        };
      }
    }, false);
  }

  public void testMethodParametersAlignmentAfterMethodNameChange() {
    getCurrentCodeStyleSettings().ALIGN_MULTILINE_PARAMETERS = true;
    getCurrentCodeStyleSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    doTest(null, "test123asd", null, new SimpleParameterGen(), new SimpleExceptionsGen(), false);
  }

  public void testMethodParametersAlignmentAfterMethodVisibilityChange() {
    getCurrentCodeStyleSettings().ALIGN_MULTILINE_PARAMETERS = true;
    getCurrentCodeStyleSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    doTest(PsiModifier.PROTECTED, null, null, new SimpleParameterGen(), new SimpleExceptionsGen(), false);
  }

  public void testMethodParametersAlignmentAfterMethodReturnTypeChange() {
    getCurrentCodeStyleSettings().ALIGN_MULTILINE_PARAMETERS = true;
    getCurrentCodeStyleSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    doTest(null, null, "Exception", new SimpleParameterGen(), new SimpleExceptionsGen(), false);
  }

  public void testVisibilityOfOverriddenMethod() {
    doTest(PsiModifier.PACKAGE_LOCAL, "foo", "void", new ParameterInfoImpl[0], new ThrownExceptionInfo[0], false);
  }

  public void testRemoveExceptions() {
    doTest(null, null, "void", new SimpleParameterGen(), new SimpleExceptionsGen(), false);
  }

  public void testPropagateParameter() {
    String basePath = "/refactoring/changeSignature/" + getTestName(false);
    configureByFile(basePath + ".java");
    final PsiElement targetElement = TargetElementUtilBase.findTargetElement(getEditor(), TargetElementUtilBase.ELEMENT_NAME_ACCEPTED);
    assertTrue("<caret> is not on method name", targetElement instanceof PsiMethod);
    PsiMethod method = (PsiMethod)targetElement;
    final PsiClass containingClass = method.getContainingClass();
    assertTrue(containingClass != null);
    final PsiMethod[] callers = containingClass.findMethodsByName("caller", false);
    assertTrue(callers.length > 0);
    final PsiMethod caller = callers[0];
    final HashSet<PsiMethod> propagateParametersMethods = new HashSet<PsiMethod>();
    propagateParametersMethods.add(caller);
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    new ChangeSignatureProcessor(getProject(), method, false, null, method.getName(),
                                 CanonicalTypes.createTypeWrapper(PsiType.VOID), new ParameterInfoImpl[]{
      new ParameterInfoImpl(0, parameters[0].getName(), parameters[0].getType()),
      new ParameterInfoImpl(-1, "b", PsiType.BOOLEAN)}, null, propagateParametersMethods, null
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

  private void doTest(@Nullable String returnType, @Nullable final String[] parameters, @Nullable final String[] exceptions, boolean delegate) {
    GenParams genParams = parameters == null ? new SimpleParameterGen() : new GenParams() {
      @Override
      public ParameterInfoImpl[] genParams(PsiMethod method) throws IncorrectOperationException {
        ParameterInfoImpl[] parameterInfos = new ParameterInfoImpl[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
          PsiType type = myFactory.createTypeFromText(parameters[i], method);
          parameterInfos[i] = new ParameterInfoImpl(-1, "p" + (i + 1), type);
        }
        return parameterInfos;
      }
    };

    GenExceptions genExceptions = exceptions == null ? new SimpleExceptionsGen() : new GenExceptions() {
      @Override
      public ThrownExceptionInfo[] genExceptions(PsiMethod method) throws IncorrectOperationException {
        ThrownExceptionInfo[] exceptionInfos = new ThrownExceptionInfo[exceptions.length];
        for (int i = 0; i < exceptions.length; i++) {
          PsiType type = myFactory.createTypeFromText(exceptions[i], method);
          exceptionInfos[i] = new JavaThrownExceptionInfo(-1, (PsiClassType)type);
        }
        return exceptionInfos;
      }
    };

    doTest(null, null, returnType, genParams, genExceptions, delegate);
  }

  private void doTest(@Nullable String newReturnType, ParameterInfoImpl[] parameterInfos, boolean generateDelegate) {
    doTest(null, null, newReturnType, parameterInfos, new ThrownExceptionInfo[0], generateDelegate);
  }

  private void doTest(@PsiModifier.ModifierConstant @Nullable String newVisibility,
                      @Nullable String newName,
                      @Nullable String newReturnType,
                      ParameterInfoImpl[] parameterInfo,
                      ThrownExceptionInfo[] exceptionInfo,
                      boolean generateDelegate) {
    SimpleParameterGen params = new SimpleParameterGen(parameterInfo);
    SimpleExceptionsGen exceptions = new SimpleExceptionsGen(exceptionInfo);
    doTest(newVisibility, newName, newReturnType, params, exceptions, generateDelegate);
  }

  private void doTest(@PsiModifier.ModifierConstant @Nullable String newVisibility,
                      @Nullable String newName,
                      @Nullable @NonNls String newReturnType,
                      GenParams genParams,
                      boolean generateDelegate) {
    doTest(newVisibility, newName, newReturnType, genParams, new SimpleExceptionsGen(), generateDelegate);
  }

  private void doTest(@PsiModifier.ModifierConstant @Nullable String newVisibility,
                      @Nullable String newName,
                      @Nullable String newReturnType,
                      GenParams genParams,
                      GenExceptions genExceptions,
                      boolean generateDelegate) {
    String basePath = "/refactoring/changeSignature/" + getTestName(false);
    configureByFile(basePath + ".java");
    PsiElement targetElement = TargetElementUtilBase.findTargetElement(getEditor(), TargetElementUtilBase.ELEMENT_NAME_ACCEPTED);
    assertTrue("<caret> is not on method name", targetElement instanceof PsiMethod);
    PsiMethod method = (PsiMethod)targetElement;
    PsiType newType = newReturnType != null ? myFactory.createTypeFromText(newReturnType, method) : method.getReturnType();
    new ChangeSignatureProcessor(getProject(), method, generateDelegate, newVisibility,
                                 newName != null ? newName : method.getName(),
                                 newType, genParams.genParams(method), genExceptions.genExceptions(method)).run();
    checkResultByFile(basePath + "_after.java");
  }

  private interface GenParams {
    ParameterInfoImpl[] genParams(PsiMethod method) throws IncorrectOperationException;
  }

  private static class SimpleParameterGen implements GenParams {
    private ParameterInfoImpl[] myInfos;

    public SimpleParameterGen() { }

    public SimpleParameterGen(ParameterInfoImpl[] infos) {
      myInfos = infos;
    }

    @Override
    public ParameterInfoImpl[] genParams(PsiMethod method) {
      if (myInfos == null) {
        myInfos = new ParameterInfoImpl[method.getParameterList().getParametersCount()];
        for (int i = 0; i < myInfos.length; i++) {
          myInfos[i] = new ParameterInfoImpl(i);
        }
      }
      for (ParameterInfoImpl info : myInfos) {
        info.updateFromMethod(method);
      }
      return myInfos;
    }
  }

  private interface GenExceptions {
    ThrownExceptionInfo[] genExceptions(PsiMethod method) throws IncorrectOperationException;
  }

  private static class SimpleExceptionsGen implements GenExceptions {
    private final ThrownExceptionInfo[] myInfos;

    public SimpleExceptionsGen() {
      myInfos = new ThrownExceptionInfo[0];
    }

    public SimpleExceptionsGen(ThrownExceptionInfo[] infos) {
      myInfos = infos;
    }

    @Override
    public ThrownExceptionInfo[] genExceptions(PsiMethod method) {
      for (ThrownExceptionInfo info : myInfos) {
        info.updateFromMethod(method);
      }
      return myInfos;
    }
  }
}
