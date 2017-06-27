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
package com.intellij.java.propertyBased;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.Nullable;
import slowCheck.*;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

@SkipSlowTestLocally
public class ApplyRandomIntentionsTest extends AbstractApplyAndRevertTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    initCompiler();
  }

  public void testOpenFilesAndRevert() throws Throwable {
    doOpenFilesAndRevert(null);
  }

  public void testOpenFilesAndRevertDeleteForeachInitializers() throws Throwable {
    doOpenFilesAndRevert(psiFile -> {
      WriteCommandAction.runWriteCommandAction(myProject, () -> PsiTreeUtil.findChildrenOfType(psiFile, PsiForStatement.class).stream()
        .limit(20)
        .forEach(stmt -> stmt.getInitialization().delete()));
    });
  }

  public void testOpenFilesAndRevertDeleteSecondArgument() throws Throwable {
    doOpenFilesAndRevert(psiFile -> {
      WriteCommandAction.runWriteCommandAction(myProject, () -> PsiTreeUtil.findChildrenOfType(psiFile, PsiCallExpression.class)
        .stream()
        .filter(PsiElement::isValid)
        .map(PsiCall::getArgumentList)
        .filter(Objects::nonNull)
        .filter(argList -> argList.getExpressions().length > 1)
        .limit(20)
        .forEach(argList -> {
          if (!argList.isValid()) return;
          PsiExpression arg = argList.getExpressions()[1];
          if (!arg.isValid()) return;
          arg.delete();
        }));
    });
  }

  public void testOpenFilesAndRevertAddNullArgument() throws Throwable {
    doOpenFilesAndRevert(psiFile -> {
      WriteCommandAction.runWriteCommandAction(myProject,
                                                      () -> PsiTreeUtil.findChildrenOfType(psiFile, PsiMethodCallExpression.class).stream()
                                                        .filter(PsiElement::isValid)
                                                        .filter(call -> call.getArgumentList().getExpressions().length > 1)
                                                        .forEach(call -> call.getArgumentList().add(JavaPsiFacade.getElementFactory(myProject).createExpressionFromText("null", call))));
    });
  }

  public void testMakeAllMethodsVoid() throws Throwable {
    doOpenFilesAndRevert(psiFile -> {
      WriteCommandAction.runWriteCommandAction(myProject,
                                               () -> PsiTreeUtil.findChildrenOfType(psiFile, PsiMethod.class).stream()
                                                        .filter(method -> method.getReturnTypeElement() != null)
                                                        .forEach(method -> method.getReturnTypeElement().replace(JavaPsiFacade.getElementFactory(myProject).createTypeElement(PsiType.VOID))));
    });
  }

  private void doOpenFilesAndRevert(@Nullable Consumer<PsiFile> mutation) throws Throwable {
    PsiManager psiManager = PsiManager.getInstance(myProject);
    CheckerSettings settings = CheckerSettings.DEFAULT_SETTINGS;
    PropertyChecker.forAll(settings.withIterationCount(10), javaFiles(), file -> {
      if (mutation == null) {
        checkCompiles(myCompilerTester.rebuild());
      }

      PsiFile psiFile = psiManager.findFile(file);
      if (psiFile == null) return false;

      Generator<InvokeIntention> genInvocation = Generator.from(data -> InvokeIntention.generate(psiFile, data)).noShrink();
      PropertyChecker.forAll(settings.withIterationCount(20), Generator.nonEmptyLists(genInvocation), list -> {
        PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
        changeAndRevert(() -> {
          if (mutation != null) {
            mutation.accept(psiFile);
          }
          documentManager.commitAllDocuments();
          long modCount = psiManager.getModificationTracker().getModificationCount();
          for (InvokeIntention invocation : list) {
            String textBefore = psiFile.getText();
            try {
              invocation.invokeIntention();
            }
            catch (Throwable e) {
              LOG.debug("File " + file.getName() + " text before applying " + invocation + ":\n" + textBefore);
              throw e;
            }
          }
          if (mutation == null && modCount != psiManager.getModificationTracker().getModificationCount()) {
            checkCompiles(myCompilerTester.make());
          }
        });
        return true;
      });

      return true;
    });
  }

  public void testModificationsInDifferentFiles() throws Throwable {
    PsiManager psiManager = PsiManager.getInstance(myProject);
    PsiModificationTracker tracker = psiManager.getModificationTracker();

    AtomicLong rebuildStamp = new AtomicLong();

    CheckerSettings settings = CheckerSettings.DEFAULT_SETTINGS.withIterationCount(30);
    Generator<InvokeIntention> genIntention =
      Generator.from(data -> InvokeIntention.generate(psiManager.findFile(javaFiles().generateValue(data)), data));

    PropertyChecker.forAll(settings, Generator.listsOf(genIntention.noShrink()), list -> {
      long startModCount = tracker.getModificationCount();
      if (rebuildStamp.getAndSet(startModCount) != startModCount) {
        checkCompiles(myCompilerTester.rebuild());
      }

      changeAndRevert(() -> {
        for (InvokeIntention invocation : list) {
          invocation.invokeIntention();
        }
        if (tracker.getModificationCount() != startModCount) {
          checkCompiles(myCompilerTester.make());
        }
      });
      return true;
    });
  }

  @Override
  protected String getTestDataPath() {
    return SystemProperties.getUserHome() + "/IdeaProjects/univocity-parsers";
  }

}
