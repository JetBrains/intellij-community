package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase;

/**
 *  @author dsl
 */
public class IntroduceVariableMultifileTest extends MultiFileTestCase {
  protected void setUp() throws Exception {
    super.setUp();
    LanguageLevelProjectExtension.getInstance(myJavaFacade.getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);
  }

  protected String getTestRoot() {
    return "/refactoring/introduceVariable/";
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testSamePackageRef() throws Exception {
    doTest(
      createAction("pack1.A",
                   new MockIntroduceVariableHandler("b", false, false, false, "pack1.B")
      )
    );
  }

  public void testGenericTypeWithInner() throws Exception {
    doTest(
      createAction("test.Client",
                   new MockIntroduceVariableHandler("l", false, true, true, "test.List<test.A.B>")
      )
    );
  }

  public void testGenericTypeWithInner1() throws Exception {
    doTest(
      createAction("test.Client",
                   new MockIntroduceVariableHandler("l", false, true, true, "test.List<test.A.B>")
      )
    );
  }

  public void testGenericWithTwoParameters() throws Exception {
    doTest(
      createAction("Client",
                   new MockIntroduceVariableHandler("p", false, false, true,
                                                    "util.Pair<java.lang.String,util.Pair<java.lang.Integer,java.lang.Boolean>>")
      )
    );
  }

  public void testGenericWithTwoParameters2() throws Exception {
    doTest(
      createAction("Client",
                   new MockIntroduceVariableHandler("p", false, false, true,
                                                    "Pair<java.lang.String,Pair<java.lang.Integer,java.lang.Boolean>>")
      )
    );
  }


  public void testDummy() {

  }

  PerformAction createAction(final String className, final IntroduceVariableBase testMe) {
    return new PerformAction() {
      public void performAction(VirtualFile vroot, VirtualFile rootAfter) {
        final JavaPsiFacade psiManager = getJavaFacade();
        final PsiClass aClass = psiManager.findClass(className, GlobalSearchScope.allScope(myProject));
        assertTrue(aClass != null);
        final PsiFile containingFile = aClass.getContainingFile();
        final VirtualFile virtualFile = containingFile.getVirtualFile();
        assertTrue(virtualFile != null);
        final Editor editor = createEditor(virtualFile);
        setupCursorAndSelection(editor);
        testMe.invoke(myProject, editor, containingFile, null);
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    };
  }
}
