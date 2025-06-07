// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.daemon.impl.quickfix.CreateClassKind;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.ide.util.DirectoryChooserUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JavaProjectRootsUtil;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.ClassKind;
import com.intellij.psi.util.CreateClassUtil;
import com.intellij.psi.util.JavaElementKind;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;

public final class CreateClassOrPackageFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  private static final Logger LOG = Logger.getInstance(CreateClassOrPackageFix.class);
  private final List<? extends PsiDirectory> myWritableDirectoryList;
  private final String myPresentation;

  private final @Nullable ClassKind myClassKind;
  private final @Nullable String mySuperClass;
  private final String myRedPart;
  private final @Nullable String myTemplateName;

  public static @Nullable CreateClassOrPackageFix createFix(final @NotNull String qualifiedName,
                                                            final @NotNull GlobalSearchScope scope,
                                                            final @NotNull PsiElement context,
                                                            final @Nullable PsiPackage basePackage,
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
    directories.removeIf(directory -> !checkCreateClassOrPackage(kind != null && !fixPath, directory, firstRedName));
    return new CreateClassOrPackageFix(directories,
                                       context,
                                       fixPath ? qualifiedName : redPart,
                                       redPart,
                                       kind,
                                       superClass,
                                       templateName);
  }

  public static @Nullable CreateClassOrPackageFix createFix(final @NotNull String qualifiedName,
                                                            final @NotNull PsiElement context,
                                                            @Nullable ClassKind kind,
                                                            String superClass) {
    return createFix(qualifiedName, context.getResolveScope(), context, null, kind, superClass, null);
  }

  private CreateClassOrPackageFix(@NotNull List<? extends PsiDirectory> writableDirectoryList,
                                  @NotNull PsiElement context,
                                  @NotNull String presentation,
                                  @NotNull String redPart,
                                  @Nullable ClassKind kind,
                                  @Nullable String superClass,
                                  final @Nullable String templateName) {
    super(context);
    myRedPart = redPart;
    myTemplateName = templateName;
    myWritableDirectoryList = writableDirectoryList;
    myClassKind = kind;
    mySuperClass = superClass;
    myPresentation = presentation;
  }

  @Override
  public @NotNull String getText() {
    return CommonQuickFixBundle.message("fix.create.title.x",
                                        (myClassKind == null ? JavaElementKind.PACKAGE : myClassKind.getElementKind()).object(),
                                        myPresentation);
  }

  @Override
  public @NotNull String getFamilyName() {
    return getText();
  }

  @Override
  public void invoke(final @NotNull Project project,
                     final @NotNull PsiFile psiFile,
                     @Nullable Editor editor,
                     final @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    if (isAvailable(project, null, psiFile)) {
      PsiDirectory directory = chooseDirectory(project, psiFile);
      if (directory == null) return;
      WriteAction.run(() -> doCreate(directory, startElement));
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

  private @Nullable PsiDirectory chooseDirectory(final Project project, final PsiFile file) {
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
          .chooseDirectory(myWritableDirectoryList.toArray(PsiDirectory.EMPTY_ARRAY),
                           preferredDirectory, project,
                           new HashMap<>());
    }
    return preferredDirectory;
  }

  private void doCreate(final PsiDirectory baseDirectory, PsiElement myContext) {
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

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  private static List<PsiDirectory> getWritableDirectoryListDefault(final @Nullable PsiPackage context,
                                                                    final GlobalSearchScope scope,
                                                                    final PsiManager psiManager) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Getting writable directory list for package '" + (context == null ? null : context.getQualifiedName()) + "', scope=" + scope);
    }
    final List<PsiDirectory> writableDirectoryList = new ArrayList<>();
    if (context != null) {
      for (PsiDirectory directory : context.getDirectories()) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Package directory: " + directory);
        }
        VirtualFile virtualFile = directory.getVirtualFile();
        if (directory.isWritable() && scope.contains(virtualFile)
            && !JavaProjectRootsUtil.isInGeneratedCode(virtualFile, psiManager.getProject())) {
          writableDirectoryList.add(directory);
        }
      }
    }
    else {
      for (VirtualFile root : JavaProjectRootsUtil.getSuitableDestinationSourceRoots(psiManager.getProject())) {
        PsiDirectory directory = psiManager.findDirectory(root);
        if (LOG.isDebugEnabled()) {
          LOG.debug("Root: " + root + ", directory: " + directory);
        }
        if (directory != null && directory.isWritable() && scope.contains(directory.getVirtualFile())) {
          writableDirectoryList.add(directory);
        }
      }
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("Result " + writableDirectoryList);
    }
    return writableDirectoryList;
  }
}
