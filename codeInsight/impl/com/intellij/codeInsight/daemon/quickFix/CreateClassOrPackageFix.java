/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateClassKind;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.GenericReference;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;

/**
 * @author peter
*/
public class CreateClassOrPackageFix implements IntentionAction, LocalQuickFix {
  private boolean myCreateClass;
  @Nullable private final String mySuperClass;
  private PsiDirectory myDirectory;
  private final List<PsiDirectory> myWritableDirectoryList;
  private final PsiElement myContext;
  private final String myCanonicalText;
  private final boolean myCreateInterface;

  public CreateClassOrPackageFix(final List<PsiDirectory> writableDirectoryList, final GenericReference reference, boolean createClass,
                                             @Nullable String superClass) {
    this(writableDirectoryList, reference.getElement(), reference.getCanonicalText(), createClass, false, superClass);
  }

  public CreateClassOrPackageFix(final PsiElement context, final String qualifiedName, final boolean createClass, final String superClass) {
    this(getWritableDirectoryListDefault(context, context.getManager()), context, qualifiedName, createClass, false, superClass);
  }

  public CreateClassOrPackageFix(final PsiElement context, final String qualifiedName, final boolean createClass, final boolean createInterface, final String superClass) {
    this(getWritableDirectoryListDefault(context, context.getManager()), context, qualifiedName, createClass, createInterface, superClass);
  }

  public CreateClassOrPackageFix(final List<PsiDirectory> writableDirectoryList, final PsiElement context, final String canonicalText, boolean createClass, boolean createInterface,
                                 @Nullable String superClass) {
    myDirectory = writableDirectoryList.isEmpty()? null : writableDirectoryList.get(0);
    myWritableDirectoryList = writableDirectoryList;
    myContext = context;
    myCreateClass = createClass;
    myCreateInterface = createInterface;
    mySuperClass = superClass;
    myCanonicalText = canonicalText;
  }

  @NotNull
  public String getText() {
    return QuickFixBundle.message(myCreateClass ? myCreateInterface ? "create.interface.text" : "create.class.text":"create.package.text",myCanonicalText);
  }

  @NotNull
  public String getName() {
    return getText();
  }

  @NotNull
  public String getFamilyName() {
    return getText();
  }

  public void applyFix(@NotNull final Project project, @NotNull ProblemDescriptor descriptor) {
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
    if (StringUtil.isEmpty(myCanonicalText)) return;

    final PsiManager manager = myDirectory.getManager();
    PsiDirectory directory = myDirectory;
    String lastName;
    for (StringTokenizer st = new StringTokenizer(myCanonicalText, "."); ; ) {
      lastName = st.nextToken();
      if (st.hasMoreTokens()) {
        try {
          directory = directory.findSubdirectory(lastName) != null?
                      directory.findSubdirectory(lastName) : directory.createSubdirectory(lastName);
        }
        catch (IncorrectOperationException e) {
          CreateFromUsageUtils.scheduleFileOrPackageCreationFailedMessageBox(e, lastName, directory, true);
          return;
        }
      }
      else {
        break;
      }
    }
    if (myCreateClass) {
      if (unitTestMode) {
        try {
          directory.createClass(lastName);
        }
        catch (IncorrectOperationException e) {
          CreateFromUsageUtils.scheduleFileOrPackageCreationFailedMessageBox(e, lastName, directory, false);
        }
      }
      else {
        CreateFromUsageUtils.createClass(
          myCreateInterface? CreateClassKind.INTERFACE : CreateClassKind.CLASS,
          directory,
          lastName,
          manager,
          myContext,
          null,
          mySuperClass
        );
      }
    }
    else {
      try {
        directory.createSubdirectory(lastName);
      }
      catch (IncorrectOperationException e) {
        CreateFromUsageUtils.scheduleFileOrPackageCreationFailedMessageBox(e, lastName, directory, true);
      }
    }
  }

  public boolean startInWriteAction() {
    return true;
  }

  public static List<PsiDirectory> getWritableDirectoryListDefault(final PsiElement context, final PsiManager psiManager) {
    final List<PsiDirectory> writableDirectoryList = new ArrayList<PsiDirectory>();
    if (context instanceof PsiPackage) {
      for (PsiDirectory directory : ((PsiPackage)context).getDirectories()) {
        if (directory.isWritable()) {
          writableDirectoryList.add(directory);
        }
      }
    }
    else {
      for (VirtualFile root : ProjectRootManager.getInstance(psiManager.getProject()).getContentSourceRoots()) {
        PsiDirectory directory = psiManager.findDirectory(root);
        if (directory != null && directory.isWritable()) {
          writableDirectoryList.add(directory);
        }
      }
    }
    return writableDirectoryList;
  }
}
