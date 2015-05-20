package com.intellij.refactoring.changeClassSignature;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.LightRefactoringTestCase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author dsl
 */
public class ChangeClassSignatureTest extends LightRefactoringTestCase {
  @NonNls private static final String DATA_PATH = "/refactoring/changeClassSignature/";

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testNoParams() throws Exception {
    doTest(new GenParams() {
      @Override
      public TypeParameterInfo[] gen(PsiClass aClass) throws IncorrectOperationException {
        return new TypeParameterInfo[]{
          new TypeParameterInfo(aClass, "T", "java.lang.String")
        };
      }
    });
  }

  public void testInstanceOf() throws Exception {
    doTest(new GenParams() {
      @Override
      public TypeParameterInfo[] gen(PsiClass aClass) throws IncorrectOperationException {
        return new TypeParameterInfo[]{
          new TypeParameterInfo(aClass, "T", "java.lang.String")
        };
      }
    });
  }

  public void testSubstituteParamInsideClass() throws Exception {
    doTest(new GenParams() {
      @Override
      public TypeParameterInfo[] gen(PsiClass aClass) throws IncorrectOperationException {
        return new TypeParameterInfo[0];
      }
    });
  }

  public void testRemoveAllParams() throws Exception {
    doTest(new GenParams() {
      @Override
      public TypeParameterInfo[] gen(PsiClass aClass) {
        return new TypeParameterInfo[0];
      }
    });
  }

  public void testReorderParams() throws Exception {
    doTest(new GenParams() {
      @Override
      public TypeParameterInfo[] gen(PsiClass aClass) {
        return new TypeParameterInfo[] {
          new TypeParameterInfo(1),
          new TypeParameterInfo(0)
        };
      }
    });
  }

  public void testAddParam() throws Exception {
    doTest(new GenParams() {
      @Override
      public TypeParameterInfo[] gen(PsiClass aClass) throws IncorrectOperationException {
        return new TypeParameterInfo[] {
          new TypeParameterInfo(0),
          new TypeParameterInfo(aClass, "E", "L<T>")
        };
      }
    });
  }

  public void testAddParamDiamond() throws Exception {
    doTest(new GenParams() {
      @Override
      public TypeParameterInfo[] gen(PsiClass aClass) throws IncorrectOperationException {
        return new TypeParameterInfo[] {
          new TypeParameterInfo(0),
          new TypeParameterInfo(aClass, "I", "Integer")
        };
      }
    });
  }

  public void testAddOneFirst() throws Exception {
    doTest(new GenParams() {
      @Override
      public TypeParameterInfo[] gen(PsiClass aClass) throws IncorrectOperationException {
        return new TypeParameterInfo[]{
          new TypeParameterInfo(aClass, "T", "java.lang.String")
        };
      }
    }, "Zero.java", "OneString.java");
  }

  public void testAddManyFirst() throws Exception {
    doTest(new GenParams() {
      @Override
      public TypeParameterInfo[] gen(PsiClass aClass) throws IncorrectOperationException {
        return new TypeParameterInfo[]{
          new TypeParameterInfo(aClass, "U", "SubjectFace"),
          new TypeParameterInfo(aClass, "V", "java.util.Set<java.lang.Object>")
        };
      }
    }, "Zero.java", "TwoSubjectFaceSetObject.java");
  }

  public void testRemoveOneLast() throws Exception {
    doTest(new GenParams() {
      @Override
      public TypeParameterInfo[] gen(PsiClass aClass) throws IncorrectOperationException {
        return new TypeParameterInfo[0];
      }
    }, "OneString.java", "Zero.java");
  }

  public void testRemoveManyLast() throws Exception {
    doTest(new GenParams() {
      @Override
      public TypeParameterInfo[] gen(PsiClass aClass) throws IncorrectOperationException {
        return new TypeParameterInfo[0];
      }
    }, "TwoSubjectFaceSetObject.java", "Zero.java");
  }

  private void doTest(GenParams gen) throws Exception {
    @NonNls final String filePathBefore = getTestName(false) + ".java";
    @NonNls final String filePathAfter = getTestName(false) + ".java.after";
    doTest(gen, filePathBefore, filePathAfter);
  }

  private void doTest(GenParams gen, @NonNls String filePathBefore, @NonNls String filePathAfter) throws Exception {
    final String filePath = DATA_PATH + filePathBefore;
    configureByFile(filePath);
    final PsiElement targetElement = TargetElementUtil.findTargetElement(getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED);
    assertTrue("<caret> is not on class name", targetElement instanceof PsiClass);
    PsiClass aClass = (PsiClass)targetElement;
    new ChangeClassSignatureProcessor(getProject(), aClass, gen.gen(aClass)).run();
    checkResultByFile(DATA_PATH + filePathAfter);
  }

  private interface GenParams {
    TypeParameterInfo[] gen(PsiClass aClass) throws IncorrectOperationException;
  }
}
