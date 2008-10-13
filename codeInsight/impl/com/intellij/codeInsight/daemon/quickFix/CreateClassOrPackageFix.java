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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.ClassKind;
import com.intellij.psi.util.CreateClassUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author peter
 */
public class CreateClassOrPackageFix implements IntentionAction, LocalQuickFix {

  private final List<PsiDirectory> myWritableDirectoryList;
  private final PsiElement myContext;
  private final String myPresentation;

  @Nullable private final ClassKind myClassKind;
  @Nullable private final String mySuperClass;
  private final String myRedPart;
  @Nullable private final String myTemplateName;

  @Nullable
  public static CreateClassOrPackageFix createFix(@NotNull final String qualifiedName,
                                                  @NotNull final GlobalSearchScope scope,
                                                  @NotNull final PsiElement context,
                                                  @Nullable final PsiPackage basePackage,
                                                  @Nullable ClassKind kind,
                                                  @Nullable String superClass,
                                                  @Nullable String templateName) {
    final List<PsiDirectory> directories = getWritableDirectoryListDefault(basePackage, scope, context.getManager());
    if (directories.isEmpty()) {
      return null;
    }
    final String redPart = basePackage == null ? qualifiedName : qualifiedName.substring(basePackage.getQualifiedName().length() + 1);
    final int dot = redPart.indexOf('.');
    final boolean fixPath = dot >= 0;
    final String firstRedName = fixPath ? redPart.substring(0, dot) : redPart;
    for (Iterator<PsiDirectory> i = directories.iterator(); i.hasNext(); ) {
      if (!checkCreateClassOrPackage(kind != null && !fixPath, i.next(), firstRedName)) {
        i.remove();
      }
    }
    return directories.isEmpty() ? null : new CreateClassOrPackageFix(directories,
                                                                      context,
                                                                      fixPath ? qualifiedName : redPart,
                                                                      redPart,
                                                                      kind,
                                                                      superClass,
                                                                      templateName);
  }

  @Nullable
  public static CreateClassOrPackageFix createFix(@NotNull final String qualifiedName,
                                                  @NotNull final PsiElement context,
                                                  @Nullable ClassKind kind, final String superClass) {
    return createFix(qualifiedName, context.getResolveScope(), context, null, kind, superClass, null);
  }

  private CreateClassOrPackageFix(final List<PsiDirectory> writableDirectoryList,
                                  final PsiElement context,
                                  final String presentation,
                                  final String redPart,
                                  @Nullable ClassKind kind,
                                  @Nullable String superClass,
                                  @Nullable final String templateName) {
    myRedPart = redPart;
    myTemplateName = templateName;
    myWritableDirectoryList = writableDirectoryList;
    myContext = context;
    myClassKind = kind;
    mySuperClass = superClass;
    myPresentation = presentation;
  }

  @NotNull
  public String getText() {
    return QuickFixBundle.message(
        myClassKind == ClassKind.INTERFACE ? "create.interface.text" : myClassKind != null ? "create.class.text" : "create.package.text",
        myPresentation);
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

  private static boolean checkCreateClassOrPackage(final boolean createJavaClass, final PsiDirectory directory, final String name) {

    try {
      if (createJavaClass) {
        JavaDirectoryService.getInstance().checkCreateClass(directory, name);
      }
      else {
        directory.checkCreateSubdirectory(name);
      }
      return true;
    }
    catch (IncorrectOperationException ex) {
      return false;
    }
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return true;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {

    final PsiDirectory directory = chooseDirectory(project, file);
    if (directory == null) return;
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        doCreate(directory);
      }
    });
  }

  @Nullable
  private PsiDirectory chooseDirectory(final Project project, final PsiFile file) {
    PsiDirectory preferredDirectory = myWritableDirectoryList.isEmpty() ? null : myWritableDirectoryList.get(0);
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final VirtualFile virtualFile = file.getVirtualFile();
    assert virtualFile != null;
    final Module moduleForFile = fileIndex.getModuleForFile(virtualFile);
    if (myWritableDirectoryList.size() > 1 && !ApplicationManager.getApplication().isUnitTestMode()) {
      if (moduleForFile != null) {
        for (PsiDirectory directory : myWritableDirectoryList) {
          if (fileIndex.getModuleForFile(directory.getVirtualFile()) == moduleForFile) {
            preferredDirectory = directory;
            break;
          }
        }
      }

      return DirectoryChooserUtil
          .chooseDirectory(myWritableDirectoryList.toArray(new PsiDirectory[myWritableDirectoryList.size()]),
                           preferredDirectory, project,
                           new HashMap<PsiDirectory, String>());
    }
    return preferredDirectory;
  }

  private void doCreate(final PsiDirectory baseDirectory) {
    final PsiManager manager = baseDirectory.getManager();
    PsiDirectory directory = baseDirectory;
    String lastName;
    for (StringTokenizer st = new StringTokenizer(myRedPart, "."); ;) {
      lastName = st.nextToken();
      if (st.hasMoreTokens()) {
        try {
          final PsiDirectory subdirectory = directory.findSubdirectory(lastName);
          directory = subdirectory != null ? subdirectory : directory.createSubdirectory(lastName);
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
      if (myTemplateName != null) {
        createdClass = CreateClassUtil.createClassFromCustomTemplate(directory, null, lastName, myTemplateName);
      }
      else {
        createdClass = CreateFromUsageUtils
            .createClass(myClassKind == ClassKind.INTERFACE ? CreateClassKind.INTERFACE : CreateClassKind.CLASS, directory, lastName,
                         manager, myContext, null, mySuperClass);
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

  public static List<PsiDirectory> getWritableDirectoryListDefault(@Nullable final PsiPackage context,
                                                                   final GlobalSearchScope scope,
                                                                   final PsiManager psiManager) {
    final List<PsiDirectory> writableDirectoryList = new ArrayList<PsiDirectory>();
    if (context != null) {
      for (PsiDirectory directory : context.getDirectories()) {
        if (directory.isWritable() && scope.contains(directory.getVirtualFile())) {
          writableDirectoryList.add(directory);
        }
      }
    }
    else {
      for (VirtualFile root : ProjectRootManager.getInstance(psiManager.getProject()).getContentSourceRoots()) {
        PsiDirectory directory = psiManager.findDirectory(root);
        if (directory != null && directory.isWritable() && scope.contains(directory.getVirtualFile())) {
          writableDirectoryList.add(directory);
        }
      }
    }
    return writableDirectoryList;
  }
}
