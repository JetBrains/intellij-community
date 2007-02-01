/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateClassKind;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.resolve.reference.impl.GenericReference;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.module.Module;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.HashMap;

/**
 * @author peter
*/
public class CreateClassOrPackageFix implements IntentionAction, LocalQuickFix {
  private boolean myCreateClass;
  @Nullable private final String mySuperClass;
  private PsiDirectory myDirectory;
  private final List<PsiDirectory> myWritableDirectoryList;
  private final PsiReference myReference;
  private final String myCanonicalText;

  public CreateClassOrPackageFix(final List<PsiDirectory> writableDirectoryList, final GenericReference reference, boolean createClass,
                                             @Nullable String superClass) {
    myDirectory = writableDirectoryList.get(0);
    myWritableDirectoryList = writableDirectoryList;
    myReference = reference;
    myCreateClass = createClass;
    mySuperClass = superClass;
    myCanonicalText = myReference.getCanonicalText();
  }

  @NotNull
  public String getText() {
    return QuickFixBundle.message(myCreateClass ? "create.class.text":"create.package.text",myCanonicalText);
  }

  @NotNull
  public String getName() {
    return getText();
  }

  @NotNull
  public String getFamilyName() {
    return getText();
  }

  public void applyFix(@NotNull final Project project, ProblemDescriptor descriptor) {
    final PsiFile file = descriptor.getPsiElement().getContainingFile();
    if (isAvailable(project, null, file)) {
      new WriteCommandAction(project) {
        protected void run(Result result) throws Throwable {
          invoke(project, null, file);
        }
      }.execute();
    }
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return true;
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    final boolean unitTestMode = ApplicationManager.getApplication().isUnitTestMode();

    if (myWritableDirectoryList.size() > 1 && !unitTestMode) {
      PsiDirectory preferredDirectory = myDirectory;
      final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
      final Module moduleForFile = fileIndex.getModuleForFile(file.getVirtualFile());

      if (moduleForFile != null) {
        for(PsiDirectory d:myWritableDirectoryList) {
          if (fileIndex.getModuleForFile(d.getVirtualFile()) == moduleForFile) {
            preferredDirectory = d;
            break;
          }
        }
      }

      myDirectory = MoveClassesOrPackagesUtil.chooseDirectory(
        myWritableDirectoryList.toArray(new PsiDirectory[myWritableDirectoryList.size()]),
        preferredDirectory,
        project,
        new HashMap<PsiDirectory, String>()
      );
    }
    if (myDirectory == null) return;

    final PsiManager manager = myDirectory.getManager();

    if (myCreateClass) {
      if (unitTestMode) {
        try {
          myDirectory.createClass(myCanonicalText);
          return;
        } catch(IncorrectOperationException ex) {
          return;
        }
      }

      CreateFromUsageUtils.createClass(
        CreateClassKind.CLASS,
        myDirectory,
        myCanonicalText,
        manager,
        myReference.getElement(),
        null,
        mySuperClass
      );
    } else {
      try {
        myDirectory.createSubdirectory(myCanonicalText);
      } catch(IncorrectOperationException ex) {
      }
    }
  }

  public boolean startInWriteAction() {
    return true;
  }
}
