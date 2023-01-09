// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.propertyBased;

import com.intellij.codeInsight.intention.impl.SealClassAction;
import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDocumentManagerImpl;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
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

import java.util.*;

public class MakeClassSealedPropertyTest extends BaseUnivocityTest {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    WriteAction.run(() -> LanguageLevelProjectExtension.getInstance(myProject).setLanguageLevel(LanguageLevel.JDK_17));
    ((PsiDocumentManagerImpl)PsiDocumentManager.getInstance(myProject)).disableBackgroundCommit(getTestRootDisposable());
    MadTestingUtil.enableAllInspections(myProject, JavaLanguage.INSTANCE);
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

      PsiDocumentManager.getInstance(myProject).commitAllDocuments();
      Set<PsiFile> relatedFiles = new HashSet<>();
      relatedFiles.add(psiFile);
      DirectClassInheritorsSearch.search(psiClass).mapping(PsiElement::getContainingFile).forEach(e -> {
        relatedFiles.add(e);
        return true;
      });
      relatedFiles.forEach(f -> assertFalse(MadTestingUtil.containsErrorElements(f.getViewProvider())));

      PsiFile fileToChange = env.generateValue(Generator.sampledFrom(relatedFiles.toArray(PsiFile.EMPTY_ARRAY)),
                                               "Invoking intention in %s");
      env.executeCommands(IntDistribution.uniform(1, 5),
                          Generator.constant(new InvokeIntention(fileToChange, new JavaGreenIntentionPolicy())));
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();
      relatedFiles.forEach(f -> assertFalse(MadTestingUtil.containsErrorElements(f.getViewProvider())));
    });
  }

  private static boolean convertToSealedClass(@NotNull Editor editor,
                                              @NotNull SealClassAction makeSealedAction,
                                              @NotNull PsiIdentifier classIdentifier) {
    try {
      PsiFile containingFile = classIdentifier.getContainingFile();
      ShowIntentionActionsHandler.chooseActionAndInvoke(containingFile, editor, makeSealedAction, makeSealedAction.getText());
      return true;
    }
    catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
      return false;
    }
  }

  private static boolean canConvertToSealedClass(@NotNull Editor editor,
                                                 @NotNull SealClassAction makeSealedAction,
                                                 @NotNull PsiClass psiClass) {
    PsiIdentifier nameIdentifier = psiClass.getNameIdentifier();
    if (nameIdentifier == null) return false;
    editor.getCaretModel().moveToOffset(nameIdentifier.getTextOffset());
    return makeSealedAction.isAvailable(psiClass.getProject(), editor, nameIdentifier);
  }
}
