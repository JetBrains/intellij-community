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

import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pass;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.introduce.inplace.AbstractInplaceIntroducer;
import com.intellij.refactoring.introduceParameter.IntroduceParameterHandler;
import com.intellij.testFramework.LightPlatformTestCase;
import org.jetbrains.annotations.NotNull;

public class InplaceIntroduceParameterTest extends AbstractJavaInplaceIntroduceTest {

  private static final String BASE_PATH = "/refactoring/inplaceIntroduceParameter/";

  public void testReplaceAll() {
    doTest(new Pass<AbstractInplaceIntroducer>() {
      @Override
      public void pass(AbstractInplaceIntroducer inplaceIntroduceFieldPopup) {
        inplaceIntroduceFieldPopup.setReplaceAllOccurrences(true);
      }
    });
  }

  public void testChainMethodCall() {
    doTest(new Pass<AbstractInplaceIntroducer>() {
      @Override
      public void pass(AbstractInplaceIntroducer inplaceIntroduceFieldPopup) {
        inplaceIntroduceFieldPopup.setReplaceAllOccurrences(true);
      }
    });
  }


  public void testReplaceAll1() {
    doTest(new Pass<AbstractInplaceIntroducer>() {
      @Override
      public void pass(AbstractInplaceIntroducer inplaceIntroduceFieldPopup) {
        inplaceIntroduceFieldPopup.setReplaceAllOccurrences(true);
      }
    });
  }

  public void testReplaceOneLeaveParamToDelete() {
    doTest(new Pass<AbstractInplaceIntroducer>() {
      @Override
      public void pass(AbstractInplaceIntroducer inplaceIntroduceFieldPopup) {
      }
    });
  }

  public void testReplaceAllBrokenIdentifier() {
    doTest(new Pass<AbstractInplaceIntroducer>() {
      @Override
      public void pass(AbstractInplaceIntroducer inplaceIntroduceFieldPopup) {
        type("ONE TWO");
        inplaceIntroduceFieldPopup.setReplaceAllOccurrences(true);
      }
    });
  }

  public void testReplaceAll2() {
    doTest(new Pass<AbstractInplaceIntroducer>() {
      @Override
      public void pass(AbstractInplaceIntroducer inplaceIntroduceFieldPopup) {
        inplaceIntroduceFieldPopup.setReplaceAllOccurrences(true);
      }
    });
  }

  public void testReplaceAll3() {
    doTest(new Pass<AbstractInplaceIntroducer>() {
      @Override
      public void pass(AbstractInplaceIntroducer inplaceIntroduceFieldPopup) {
        inplaceIntroduceFieldPopup.setReplaceAllOccurrences(true);
      }
    });
  }

  public void testReplaceAllMethodCalls() {
    doTest(new Pass<AbstractInplaceIntroducer>() {
      @Override
      public void pass(AbstractInplaceIntroducer inplaceIntroducePopup) {
        inplaceIntroducePopup.setReplaceAllOccurrences(true);
        type("string");
      }
    });
  }

  public void testParamNameEqMethodName() {
    doTest(new Pass<AbstractInplaceIntroducer>() {
      @Override
      public void pass(AbstractInplaceIntroducer inplaceIntroducePopup) {
      }
    });
  }

  public void testLocalInsideAnonymous() {
    doTest(new Pass<AbstractInplaceIntroducer>() {
      @Override
      public void pass(AbstractInplaceIntroducer inplaceIntroducePopup) {
      }
    });
  }

  public void testNoConflictingVariableDueToReparse() {
    doTest(new Pass<AbstractInplaceIntroducer>() {
      @Override
      public void pass(AbstractInplaceIntroducer inplaceIntroducePopup) {
      }
    });
  }

  public void testLocalInsideAnonymous1() {
    final Pass<AbstractInplaceIntroducer> pass = new Pass<AbstractInplaceIntroducer>() {
      @Override
      public void pass(AbstractInplaceIntroducer inplaceIntroducePopup) {
      }
    };
    String name = getTestName(true);
    configureByFile(getBasePath() + name + getExtension());
    final boolean enabled = getEditor().getSettings().isVariableInplaceRenameEnabled();
    try {
      TemplateManagerImpl.setTemplateTesting(getProject(), getTestRootDisposable());
      getEditor().getSettings().setVariableInplaceRenameEnabled(true);

      //ensure extract local var
      final MyIntroduceHandler introduceHandler = createIntroduceHandler();
      introduceHandler.invokeImpl(LightPlatformTestCase.getProject(), getLocalVariableFromEditor(), getEditor());
      final AbstractInplaceIntroducer introducer = introduceHandler.getInplaceIntroducer();
      pass.pass(introducer);
      TemplateState state = TemplateManagerImpl.getTemplateState(getEditor());
      assert state != null;
      state.gotoEnd(false);
      checkResultByFile(getBasePath() + name + "_after" + getExtension());
    }
    finally {
      getEditor().getSettings().setVariableInplaceRenameEnabled(enabled);
    }
  }

  @Override
  protected String getBasePath() {
    return BASE_PATH;
  }

  @Override
  protected MyIntroduceHandler createIntroduceHandler() {
    return new MyIntroduceParameterHandler();
  }

  public void testEscapePosition() {
    doTestEscape();
  }

  public void testEscapePositionOnLocal() {
    doTestEscape();
  }

  public void testExtractParamOverLocal() {
    doTest(new Pass<AbstractInplaceIntroducer>() {
      @Override
      public void pass(AbstractInplaceIntroducer abstractInplaceIntroducer) {
      }
    });
  }

  public void testExtractConflictingParamOverLocal() {
    try {
      doTest(new Pass<AbstractInplaceIntroducer>() {
        @Override
        public void pass(AbstractInplaceIntroducer abstractInplaceIntroducer) {
          type("p");
        }
      });
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("There is already a parameter <b><code>p</code></b>. It will conflict with the introduced parameter", e.getMessage());
      return;
    }
    fail("Conflict expected");
  }

  private static class MyIntroduceParameterHandler extends IntroduceParameterHandler implements MyIntroduceHandler {

    @Override
    public boolean invokeImpl(Project project, @NotNull PsiExpression selectedExpr, Editor editor) {
      return super.invokeImpl(project, selectedExpr, editor);
    }

    @Override
    public boolean invokeImpl(Project project, PsiLocalVariable localVariable, Editor editor) {
      return super.invokeImpl(project, localVariable, editor);
    }
  }
}
