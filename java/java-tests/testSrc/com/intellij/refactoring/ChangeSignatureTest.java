package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.psi.*;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.JavaThrownExceptionInfo;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.refactoring.changeSignature.ThrownExceptionInfo;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

/**
 * @author dsl
 */
public class ChangeSignatureTest extends LightCodeInsightTestCase {
  public void testSimple() throws Exception {
    doTest(null, null, null, new ParameterInfoImpl[0], new ThrownExceptionInfo[0], false);
  }

  public void testParameterReorder() throws Exception {
    doTest(null, new ParameterInfoImpl[]{new ParameterInfoImpl(1), new ParameterInfoImpl(0)}, false);
  }

  public void testGenericTypes() throws Exception {
    doTest(null, null, "T", new GenParams() {
      public ParameterInfoImpl[] genParams(PsiMethod method) throws IncorrectOperationException {
        final PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
        return new ParameterInfoImpl[]{
          new ParameterInfoImpl(-1, "x", factory.createTypeFromText("T", method.getParameterList()), "null"),
          new ParameterInfoImpl(-1, "y", factory.createTypeFromText("C<T>", method.getParameterList()), "null")
        };
      }
    }, false);
  }

  public void testGenericTypesInOldParameters() throws Exception {
    doTest(null, null, null, new GenParams() {
      public ParameterInfoImpl[] genParams(PsiMethod method) throws IncorrectOperationException {
        final PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
        return new ParameterInfoImpl[] {
          new ParameterInfoImpl(0, "t", factory.createTypeFromText("T", method), null)
        };
      }
    }, false);
  }

  public void testTypeParametersInMethod() throws Exception {
    doTest(null, null, null, new GenParams() {
             public ParameterInfoImpl[] genParams(PsiMethod method) throws IncorrectOperationException {
               final PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
               return new ParameterInfoImpl[]{
                   new ParameterInfoImpl(-1, "t", factory.createTypeFromText("T", method.getParameterList()), "null"),
                   new ParameterInfoImpl(-1, "u", factory.createTypeFromText("U", method.getParameterList()), "null"),
                   new ParameterInfoImpl(-1, "cu", factory.createTypeFromText("C<U>", method.getParameterList()), "null")
                 };
             }
           }, false);
  }

  public void testDefaultConstructor() throws Exception {
    doTest(null,
           new ParameterInfoImpl[] {
              new ParameterInfoImpl(-1, "j", PsiType.INT, "27")
           }, false);
  }

  public void testGenerateDelegate() throws Exception {
    doTest(null,
           new ParameterInfoImpl[] {
             new ParameterInfoImpl(-1, "i", PsiType.INT, "27")
           }, true);
  }

  public void testGenerateDelegateForAbstract() throws Exception {
    doTest(null,
           new ParameterInfoImpl[] {
             new ParameterInfoImpl(-1, "i", PsiType.INT, "27")
           }, true);
  }

  public void testGenerateDelegateWithReturn() throws Exception {
    doTest(null,
           new ParameterInfoImpl[] {
             new ParameterInfoImpl(-1, "i", PsiType.INT, "27")
           }, true);
  }

  public void testGenerateDelegateWithParametersReordering() throws Exception {
    doTest(null,
           new ParameterInfoImpl[] {
             new ParameterInfoImpl(1),
             new ParameterInfoImpl(-1, "c", PsiType.CHAR, "'a'"),
             new ParameterInfoImpl(0, "j", PsiType.INT)
           }, true);
  }

  public void testGenerateDelegateConstructor() throws Exception {
    doTest(null, new ParameterInfoImpl[0], true);
  }

  public void testGenerateDelegateDefaultConstructor() throws Exception {
    doTest(null, new ParameterInfoImpl[] {
      new ParameterInfoImpl(-1, "i", PsiType.INT, "27")
    }, true);
  }

  public void testSCR40895() throws Exception {
    doTest(null, new ParameterInfoImpl[] {
      new ParameterInfoImpl(0, "y", PsiType.INT),
      new ParameterInfoImpl(1, "b", PsiType.BOOLEAN)
    }, false);
  }

  public void testSuperCallFromOtherMethod() throws Exception {
    doTest(null, new ParameterInfoImpl[] {
      new ParameterInfoImpl(-1, "nnn", PsiType.INT, "-222"),
    }, false);
  }

  public void testUseAnyVariable() throws Exception {
    doTest(null, null, null, new GenParams() {
      public ParameterInfoImpl[] genParams(PsiMethod method) throws IncorrectOperationException {
        final PsiElementFactory factory = JavaPsiFacade.getInstance(method.getProject()).getElementFactory();
        return new ParameterInfoImpl[] {
          new ParameterInfoImpl(-1, "l", factory.createTypeFromText("List", method), "null", true)
        };
      }
    }, false);
  }

  public void testRemoveVarargParameter() throws Exception {
    doTest(null, null, null, new ParameterInfoImpl[]{new ParameterInfoImpl(0)}, new ThrownExceptionInfo[0], false);
  }

  public void testEnumConstructor() throws Exception {
    doTest(null, new ParameterInfoImpl[] {
      new ParameterInfoImpl(-1, "i", PsiType.INT, "10")
    }, false);
  }

  public void testVarargs1() throws Exception {
    doTest(null, new ParameterInfoImpl[] {
      new ParameterInfoImpl(-1, "b", PsiType.BOOLEAN, "true"),
      new ParameterInfoImpl(0)
    }, false);
  }

  public void testCovariantReturnType() throws Exception {
    doTest("java.lang.Runnable", new ParameterInfoImpl[0], false);
  }

  public void testReorderExceptions() throws Exception {
    doTest(null, null, null, new SimpleParameterGen(new ParameterInfoImpl[0]),
           new SimpleExceptionsGen(new ThrownExceptionInfo[]{new JavaThrownExceptionInfo(1), new JavaThrownExceptionInfo(0)}),
           false);
  }

  public void testAlreadyHandled() throws Exception {
    doTest(null, null, null, new SimpleParameterGen(new ParameterInfoImpl[0]),
           new GenExceptions() {
             public ThrownExceptionInfo[] genExceptions(PsiMethod method) {
               return new ThrownExceptionInfo[] {
                 new JavaThrownExceptionInfo(-1, JavaPsiFacade.getInstance(method.getProject()).getElementFactory().createTypeByFQClassName("java.lang.Exception", method.getResolveScope()))
               };
             }
           },
           false);
  }

  public void testAddRuntimeException() throws Exception {
    doTest(null, null, null, new SimpleParameterGen(new ParameterInfoImpl[0]),
           new GenExceptions() {
             public ThrownExceptionInfo[] genExceptions(PsiMethod method) {
               return new ThrownExceptionInfo[] {
                 new JavaThrownExceptionInfo(-1, JavaPsiFacade.getInstance(method.getProject()).getElementFactory().createTypeByFQClassName("java.lang.RuntimeException", method.getResolveScope()))
               };
             }
           },
           false);
  }

  public void testAddException() throws Exception {
    doTest(null, null, null, new SimpleParameterGen(new ParameterInfoImpl[0]),
           new GenExceptions() {
             public ThrownExceptionInfo[] genExceptions(PsiMethod method) {
               return new ThrownExceptionInfo[] {
                 new JavaThrownExceptionInfo(-1, JavaPsiFacade.getInstance(method.getProject()).getElementFactory().createTypeByFQClassName("java.lang.Exception", method.getResolveScope()))
               };
             }
           },
           false);
  }

  public void testReorderWithVarargs() throws Exception {  // IDEADEV-26977
    final PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
    doTest(null, new ParameterInfoImpl[] {
        new ParameterInfoImpl(1),
        new ParameterInfoImpl(0, "s", factory.createTypeFromText("java.lang.String...", getFile()))
    }, false);
  }

  public void testIntroduceParameterWithDefaultValueInHierarchy() throws Exception {
    doTest(null, new ParameterInfoImpl[]{new ParameterInfoImpl(-1, "i", PsiType.INT, "0")}, false);
  }

  public void testReorderMultilineMethodParameters() throws Exception {
    // Inspired by IDEA-54902
    doTest(null, new ParameterInfoImpl[] {new ParameterInfoImpl(1), new ParameterInfoImpl(0)}, false);
  }

  public void testReplaceVarargWithArray() throws Exception {
    doTest(null, null, null, new GenParams() {
      public ParameterInfoImpl[] genParams(PsiMethod method) throws IncorrectOperationException {
        final PsiElementFactory factory = JavaPsiFacade.getInstance(method.getProject()).getElementFactory();
        return new ParameterInfoImpl[] {
          new ParameterInfoImpl(1, "l", factory.createTypeFromText("List<T>[]", method.getParameterList()), "null", false),
          new ParameterInfoImpl(0, "s", factory.createTypeFromText("String", method.getParameterList()))
        };
      }
    }, false);
  }

  private void doTest(String newReturnType, ParameterInfoImpl[] parameterInfos, final boolean generateDelegate) throws Exception {
    doTest(null, null, newReturnType, parameterInfos, new ThrownExceptionInfo[0], generateDelegate);
  }

  private void doTest(String newVisibility,
                      String newName,
                      String newReturnType,
                      ParameterInfoImpl[] parameterInfo,
                      ThrownExceptionInfo[] exceptionInfo,
                      final boolean generateDelegate) throws Exception {
    doTest(newVisibility, newName, newReturnType, new SimpleParameterGen(parameterInfo), new SimpleExceptionsGen(exceptionInfo), generateDelegate);
  }

  private void doTest(String newVisibility, String newName, @NonNls String newReturnType, GenParams gen, final boolean generateDelegate) throws Exception {
    doTest(newVisibility, newName, newReturnType, gen, new SimpleExceptionsGen(), generateDelegate);
  }

  private void doTest(String newVisibility, String newName, String newReturnType, GenParams genParams, GenExceptions genExceptions, final boolean generateDelegate) throws Exception {
    String basePath = "/refactoring/changeSignature/" + getTestName(false);
    @NonNls final String filePath = basePath + ".java";
    configureByFile(filePath);
    final PsiElement targetElement = TargetElementUtilBase.findTargetElement(getEditor(), TargetElementUtilBase.ELEMENT_NAME_ACCEPTED);
    assertTrue("<caret> is not on method name", targetElement instanceof PsiMethod);
    PsiMethod method = (PsiMethod) targetElement;
    final PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
    PsiType newType = newReturnType != null ? factory.createTypeFromText(newReturnType, method) : method.getReturnType();
    new ChangeSignatureProcessor(getProject(), method, generateDelegate, newVisibility,
                                 newName != null ? newName : method.getName(),
                                 newType, genParams.genParams(method), genExceptions.genExceptions(method)).run();
    @NonNls String after = basePath + "_after.java";
    checkResultByFile(after);
  }

  private interface GenParams {
    ParameterInfoImpl[] genParams(PsiMethod method) throws IncorrectOperationException;
  }

  private static class SimpleParameterGen implements GenParams {
    private final ParameterInfoImpl[] myInfos;

    private SimpleParameterGen(ParameterInfoImpl[] infos) {
      myInfos = infos;
    }

    public ParameterInfoImpl[] genParams(PsiMethod method) {
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

    private SimpleExceptionsGen(ThrownExceptionInfo[] infos) {
      myInfos = infos;
    }

    public ThrownExceptionInfo[] genExceptions(PsiMethod method) {
      for (ThrownExceptionInfo info : myInfos) {
        info.updateFromMethod(method);
      }
      return myInfos;
    }
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

}
