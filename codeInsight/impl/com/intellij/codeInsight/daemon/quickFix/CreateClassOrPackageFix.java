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
import com.intellij.ide.util.DirectoryChooserUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReference;
import com.intellij.psi.util.ClassKind;
import com.intellij.psi.util.CreateClassUtil;
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
  @Nullable private final String mySuperClass;
  private PsiDirectory myDirectory;
  private final List<PsiDirectory> myWritableDirectoryList;
  private final PsiElement myContext;
  private final String myCanonicalText;
  @Nullable private final ClassKind myClassKind;
  @Nullable private final String myTemplateName;

  public CreateClassOrPackageFix(final List<PsiDirectory> writableDirectoryList, final JavaClassReference reference, @Nullable ClassKind kind,
                                             @Nullable String superClass, @Nullable String templateName) {
    this(writableDirectoryList, reference.getElement(), reference.getCanonicalText(), kind, superClass, templateName);
  }

  public CreateClassOrPackageFix(final PsiElement context, final String qualifiedName, @Nullable ClassKind kind, final String superClass) {
    this(getWritableDirectoryListDefault(context, context.getManager()), context, qualifiedName, kind, superClass, null);
  }

  private CreateClassOrPackageFix(final List<PsiDirectory> writableDirectoryList, final PsiElement context, final String canonicalText,
                                  @Nullable ClassKind kind, @Nullable String superClass, @Nullable final String templateName) {
    myTemplateName = templateName;
    myDirectory = writableDirectoryList.isEmpty() ? null : writableDirectoryList.get(0);
    myWritableDirectoryList = writableDirectoryList;
    myContext = context;
    myClassKind = kind;
    mySuperClass = superClass;
    myCanonicalText = canonicalText;
  }

  @NotNull
  public String getText() {
    return QuickFixBundle.message(myClassKind == ClassKind.INTERFACE ? "create.interface.text" : myClassKind != null ? "create.class.text" : "create.package.text",myCanonicalText);
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

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return true;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    final boolean unitTestMode = ApplicationManager.getApplication().isUnitTestMode();

    PsiDirectory preferredDirectory = myDirectory;
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final Module moduleForFile = fileIndex.getModuleForFile(file.getVirtualFile());
    if (myWritableDirectoryList.size() > 1 && !unitTestMode) {
      if (moduleForFile != null) {
        for(PsiDirectory d:myWritableDirectoryList) {
          if (fileIndex.getModuleForFile(d.getVirtualFile()) == moduleForFile) {
            preferredDirectory = d;
            break;
          }
        }
      }

      myDirectory = DirectoryChooserUtil.chooseDirectory(
        myWritableDirectoryList.toArray(new PsiDirectory[myWritableDirectoryList.size()]),
        preferredDirectory,
        project,
        new HashMap<PsiDirectory, String>()
      );
    }
    if (myDirectory == null) return;
    if (StringUtil.isEmpty(myCanonicalText)) return;

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        doCreate(moduleForFile);
      }
    });
  }

  private void doCreate(Module moduleForFile) {
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
    if (myClassKind != null) {
      PsiClass createdClass;
      if (myTemplateName != null && moduleForFile != null) {
        createdClass = CreateClassUtil.createClassFromCustomTemplate(directory, moduleForFile, lastName, myTemplateName);
      }
      else {
        createdClass = CreateFromUsageUtils.createClass(myClassKind == ClassKind.INTERFACE ? CreateClassKind.INTERFACE : CreateClassKind.CLASS,
                                         directory,
                                         lastName,
                                         manager,
                                         myContext,
                                         null,
                                         mySuperClass
        );
      }
      if (createdClass != null) {
        createdClass.navigate(true);
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
    return false;
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
