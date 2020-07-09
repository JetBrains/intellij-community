// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.propertyBased;

import com.intellij.codeInsight.intention.impl.SealClassAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDocumentManagerImpl;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.testFramework.propertyBased.InvokeIntention;
import com.intellij.testFramework.propertyBased.MadTestingUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jetCheck.Generator;
import org.jetbrains.jetCheck.ImperativeCommand;
import org.jetbrains.jetCheck.IntDistribution;
import org.jetbrains.jetCheck.PropertyChecker;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class MakeClassSealedPropertyTest extends BaseUnivocityTest {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    WriteAction.run(() -> LanguageLevelProjectExtension.getInstance(myProject).setLanguageLevel(LanguageLevel.JDK_15_PREVIEW));
    ((PsiDocumentManagerImpl)PsiDocumentManager.getInstance(myProject)).disableBackgroundCommit(getTestRootDisposable());
    MadTestingUtil.enableAllInspections(myProject);
  }

  public void testMakeClassSealed() {
    RecursionManager.disableMissedCacheAssertions(getTestRootDisposable());

    PropertyChecker.customized()
      .withIterationCount(30)
      .checkScenarios(() -> this::doTestMakeClassSealed);
  }

  private void doTestMakeClassSealed(@NotNull ImperativeCommand.Environment env) {
    Generator<PsiJavaFile> javaFiles = psiJavaFiles();
    PsiJavaFile psiFile = env.generateValue(javaFiles, "Open %s in editor");

    SealClassAction makeSealedAction = new SealClassAction();
    FileEditorManager editorManager = FileEditorManager.getInstance(myProject);
    Editor editor = editorManager.openTextEditor(new OpenFileDescriptor(myProject, psiFile.getVirtualFile()), true);

    Collection<PsiClass> classes = PsiTreeUtil.findChildrenOfType(psiFile, PsiClass.class);
    List<PsiClass> psiClasses = ContainerUtil.filter(classes, psiClass -> canConvertToSealedClass(editor, makeSealedAction, psiClass));
    if (psiClasses.isEmpty()) {
      env.logMessage("Haven't found any suitable classes, skipping");
      return;
    }

    MadTestingUtil.changeAndRevert(myProject, () -> {
      PsiClass psiClass = env.generateValue(Generator.sampledFrom(psiClasses), "Converting class: %s");
      PsiIdentifier classIdentifier = Objects.requireNonNull(psiClass.getNameIdentifier());
      editor.getCaretModel().moveToOffset(classIdentifier.getTextOffset());

      boolean convertedToSealed = convertToSealedClass(editor, makeSealedAction, classIdentifier);
      if (!convertedToSealed) {
        env.logMessage("Failed to convert to sealed class, skipping");
        return;
      }

      FileViewProvider viewProvider = psiFile.getViewProvider();
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();
      assertFalse(MadTestingUtil.containsErrorElements(viewProvider));

      JavaGreenIntentionPolicy intentionPolicy = new JavaGreenIntentionPolicy() {
        @Override
        protected boolean shouldSkipIntention(@NotNull String actionText) {
          return super.shouldSkipIntention(actionText) || actionText.equals("Add error message");
        }
      };
      env.executeCommands(IntDistribution.uniform(1, 5), Generator.constant(new InvokeIntention(psiFile, intentionPolicy)));
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();
      assertFalse(MadTestingUtil.containsErrorElements(viewProvider));
    });
  }

  private static boolean convertToSealedClass(@NotNull Editor editor,
                                              @NotNull SealClassAction makeSealedAction,
                                              @NotNull PsiIdentifier classIdentifier) {
    Project project = classIdentifier.getProject();
    return WriteCommandAction.runWriteCommandAction(project, (Computable<Boolean>)() -> {
      try {
        makeSealedAction.invoke(project, editor, classIdentifier);
        return true;
      }
      catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
        return false;
      }
    });
  }

  private static boolean canConvertToSealedClass(@NotNull Editor editor,
                                                 @NotNull SealClassAction makeSealedAction,
                                                 @NotNull PsiClass psiClass) {
    PsiIdentifier nameIdentifier = psiClass.getNameIdentifier();
    if (nameIdentifier == null) return false;
    editor.getCaretModel().moveToOffset(nameIdentifier.getTextOffset());
    if (!makeSealedAction.isAvailable(psiClass.getProject(), editor, nameIdentifier)) return false;
    // for interface without implementations red code is produced
    return !psiClass.isInterface() || ClassInheritorsSearch.search(psiClass).findFirst() != null;
  }
}
