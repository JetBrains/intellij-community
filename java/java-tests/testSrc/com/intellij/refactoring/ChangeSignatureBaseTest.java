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
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.JavaThrownExceptionInfo;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.refactoring.changeSignature.ThrownExceptionInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ChangeSignatureBaseTest extends LightRefactoringTestCase {
  protected PsiElementFactory myFactory;

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

  protected void doTest(@Nullable String returnType,
                        @Nullable final String[] parameters,
                        @Nullable final String[] exceptions,
                        boolean delegate) {
    GenParams genParams = parameters == null ? new SimpleParameterGen() : method -> {
      ParameterInfoImpl[] parameterInfos = new ParameterInfoImpl[parameters.length];
      for (int i = 0; i < parameters.length; i++) {
        PsiType type = myFactory.createTypeFromText(parameters[i], method);
        parameterInfos[i] = new ParameterInfoImpl(-1, "p" + (i + 1), type);
      }
      return parameterInfos;
    };

    GenExceptions genExceptions = exceptions == null ? new SimpleExceptionsGen() : method -> {
      ThrownExceptionInfo[] exceptionInfos = new ThrownExceptionInfo[exceptions.length];
      for (int i = 0; i < exceptions.length; i++) {
        PsiType type = myFactory.createTypeFromText(exceptions[i], method);
        exceptionInfos[i] = new JavaThrownExceptionInfo(-1, (PsiClassType)type);
      }
      return exceptionInfos;
    };

    doTest(null, null, returnType, genParams, genExceptions, delegate);
  }

  protected void doTest(@Nullable String newReturnType, ParameterInfoImpl[] parameterInfos, boolean generateDelegate) {
    doTest(null, null, newReturnType, parameterInfos, new ThrownExceptionInfo[0], generateDelegate);
  }

  protected void doTest(@PsiModifier.ModifierConstant @Nullable String newVisibility,
                        @Nullable String newName,
                        @Nullable String newReturnType,
                        ParameterInfoImpl[] parameterInfo,
                        ThrownExceptionInfo[] exceptionInfo,
                        boolean generateDelegate) {
    SimpleParameterGen params = new SimpleParameterGen(parameterInfo);
    SimpleExceptionsGen exceptions = new SimpleExceptionsGen(exceptionInfo);
    doTest(newVisibility, newName, newReturnType, params, exceptions, generateDelegate);
  }

  protected void doTest(@PsiModifier.ModifierConstant @Nullable String newVisibility,
                        @Nullable String newName,
                        @Nullable @NonNls String newReturnType,
                        GenParams genParams,
                        boolean generateDelegate) {
    doTest(newVisibility, newName, newReturnType, genParams, new SimpleExceptionsGen(), generateDelegate);
  }

  protected void doTest(@PsiModifier.ModifierConstant @Nullable String newVisibility,
                        @Nullable String newName,
                        @Nullable String newReturnType,
                        GenParams genParams,
                        GenExceptions genExceptions,
                        boolean generateDelegate) {
    doTest(newVisibility, newName, newReturnType, genParams, genExceptions, generateDelegate, false);
  }

  protected void doTest(@PsiModifier.ModifierConstant @Nullable String newVisibility,
                        @Nullable String newName,
                        @Nullable String newReturnType,
                        GenParams genParams,
                        GenExceptions genExceptions,
                        boolean generateDelegate,
                        boolean skipConflict) {
    String basePath = getRelativePath() + getTestName(false);
    configureByFile(basePath + ".java");
    PsiElement targetElement = TargetElementUtil.findTargetElement(getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED);
    assertTrue("<caret> is not on method name", targetElement instanceof PsiMethod);
    PsiMethod method = (PsiMethod)targetElement;
    PsiType newType = newReturnType != null ? myFactory.createTypeFromText(newReturnType, method) : method.getReturnType();
    new ChangeSignatureProcessor(getProject(), method, generateDelegate, newVisibility,
                                 newName != null ? newName : method.getName(),
                                 newType, genParams.genParams(method), genExceptions.genExceptions(method)) {
      @Override
      protected boolean preprocessUsages(@NotNull Ref<UsageInfo[]> refUsages) {
        try {
          return super.preprocessUsages(refUsages);
        }
        catch (ConflictsInTestsException e) {
          if (skipConflict) {
            return true;
          }
          throw e;
        }
      }
    }.run();
    checkResultByFile(basePath + "_after.java");
  }

  protected String getRelativePath() {
    return "/refactoring/changeSignature/";
  }

  protected interface GenParams {
    ParameterInfoImpl[] genParams(PsiMethod method) throws IncorrectOperationException;
  }

  protected interface GenExceptions {
    ThrownExceptionInfo[] genExceptions(PsiMethod method) throws IncorrectOperationException;
  }

  protected static class SimpleParameterGen implements GenParams {
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

  protected static class SimpleExceptionsGen implements GenExceptions {
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
