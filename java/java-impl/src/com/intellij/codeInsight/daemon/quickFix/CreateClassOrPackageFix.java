/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateClassKind;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.ide.util.DirectoryChooserUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
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
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author peter
 */
public class CreateClassOrPackageFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.quickFix.CreateClassOrPackageFix");
  private final List<PsiDirectory> myWritableDirectoryList;
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
    return new CreateClassOrPackageFix(directories,
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
                                                  @Nullable ClassKind kind,
                                                  String superClass) {
    return createFix(qualifiedName, context.getResolveScope(), context, null, kind, superClass, null);
  }

  private CreateClassOrPackageFix(@NotNull List<PsiDirectory> writableDirectoryList,
                                  @NotNull PsiElement context,
                                  @NotNull String presentation,
                                  @NotNull String redPart,
                                  @Nullable ClassKind kind,
                                  @Nullable String superClass,
                                  @Nullable final String templateName) {
    super(context);
    myRedPart = redPart;
    myTemplateName = templateName;
    myWritableDirectoryList = writableDirectoryList;
    myClassKind = kind;
    mySuperClass = superClass;
    myPresentation = presentation;
  }

  @Override
  @NotNull
  public String getText() {
    return QuickFixBundle.message(
      myClassKind == ClassKind.INTERFACE ? "create.interface.text" : myClassKind != null ? "create.class.text" : "create.package.text",
      myPresentation);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return getText();
  }

  @Override
  public void invoke(@NotNull final Project project,
                     @NotNull final PsiFile file,
                     @Nullable("is null when called from inspection") Editor editor,
                     @NotNull final PsiElement startElement,
                     @NotNull PsiElement endElement) {
    if (isAvailable(project, null, file)) {
      new WriteCommandAction(project) {
        @Override
        protected void run(@NotNull Result result) throws Throwable {
          final PsiDirectory directory = chooseDirectory(project, file);
          if (directory == null) return;
          ApplicationManager.getApplication().runWriteAction(() -> doCreate(directory, startElement));
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

  public static List<PsiDirectory> getWritableDirectoryListDefault(@Nullable final PsiPackage context,
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
