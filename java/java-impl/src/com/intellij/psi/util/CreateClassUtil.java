/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.psi.util;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.JavaCreateFromTemplateHandler;
import com.intellij.ide.util.DirectoryChooserUtil;
import com.intellij.ide.util.PackageUtil;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.impl.DirectoryIndex;
import com.intellij.openapi.roots.impl.DirectoryInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.StringTokenizer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * author: lesya
 */
public class CreateClassUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.j2ee.CreateClassUtil");

  @NonNls public static final String DEFAULT_CLASS_TEMPLATE = "#DEFAULT_CLASS_TEMPLATE";
  @NonNls private static final String DO_NOT_CREATE_CLASS_TEMPLATE = "#DO_NOT_CREATE_CLASS_TEMPLATE";
  @NonNls private static final String CLASS_NAME_PROPERTY = "Class_Name";
  @NonNls private static final String INTERFACE_NAME_PROPERTY = "Interface_Name";

  private CreateClassUtil() {}

  @Nullable
  private static PsiClass createClassFromTemplate(@NotNull final Properties attributes, @Nullable final String templateName,
                                                  @NotNull final PsiDirectory directoryRoot,
                                                  @NotNull final String className) throws IncorrectOperationException {
    if (templateName == null) return null;
    if (templateName.equals(DO_NOT_CREATE_CLASS_TEMPLATE)) return null;

    final Project project = directoryRoot.getProject();
    try {
      final PsiDirectory directory = createParentDirectories(directoryRoot, className);
      final PsiFile psiFile = directory.findFile(className + "." + StdFileTypes.JAVA.getDefaultExtension());
      if (psiFile != null) {
        psiFile.delete();
      }

      final String rawClassName = extractClassName(className);
      final PsiFile existing = directory.findFile(rawClassName + ".java");
      if (existing instanceof PsiJavaFile) {
        final PsiClass[] classes = ((PsiJavaFile)existing).getClasses();
        if (classes.length > 0) {
          return classes[0];
        }
        return null;
      }

      final PsiClass aClass;
      if (templateName.equals(DEFAULT_CLASS_TEMPLATE)) {
        aClass = JavaDirectoryService.getInstance().createClass(directory, rawClassName);
      }
      else {
        final FileTemplateManager fileTemplateManager = FileTemplateManager.getInstance();
        FileTemplate fileTemplate = fileTemplateManager.getJ2eeTemplate(templateName);
        LOG.assertTrue(fileTemplate != null, templateName + " not found");
        final String text = fileTemplate.getText(attributes);
        aClass = JavaCreateFromTemplateHandler.createClassOrInterface(project, directory, text, true, fileTemplate.getExtension());
      }
      return (PsiClass)JavaCodeStyleManager.getInstance(project).shortenClassReferences(aClass);
    }
    catch (IOException e) {
      throw new IncorrectOperationException(e.toString(), e);
    }
  }

  @NotNull
  private static PsiDirectory createParentDirectories(@NotNull PsiDirectory directoryRoot, @NotNull String className) throws IncorrectOperationException {
    final PsiPackage currentPackage = JavaDirectoryService.getInstance().getPackage(directoryRoot);
    final String packagePrefix = currentPackage == null? null : currentPackage.getQualifiedName() + ".";
    final String packageName = extractPackage(packagePrefix != null && className.startsWith(packagePrefix)?
                                              className.substring(packagePrefix.length()) : className);
    final StringTokenizer tokenizer = new StringTokenizer(packageName, ".");
    while (tokenizer.hasMoreTokens()) {
      String packagePart = tokenizer.nextToken();
      PsiDirectory subdirectory = directoryRoot.findSubdirectory(packagePart);
      if (subdirectory == null) {
        directoryRoot.checkCreateSubdirectory(packagePart);
        subdirectory = directoryRoot.createSubdirectory(packagePart);
      }
      directoryRoot = subdirectory;
    }
    return directoryRoot;
  }

  @Nullable
  public static PsiDirectory getRootDirectory(PsiClass aClass) {
    return getSourceRootDirectory(aClass.getContainingFile().getContainingDirectory());
  }

  @Nullable
  private static PsiDirectory getSourceRootDirectory(PsiDirectory directory) {
    PsiManager manager = directory.getManager();
    DirectoryIndex directoryIndex = DirectoryIndex.getInstance(manager.getProject());
    DirectoryInfo info = directoryIndex.getInfoForDirectory(directory.getVirtualFile());
    if (info == null || info.sourceRoot == null) return null;
    return manager.findDirectory(info.sourceRoot);
  }

  @Nullable
  public static PsiDirectory obtainDirectoryRootForPackage(final Module module, final String packageName) {
    final Project project = module.getProject();
    GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module);
    final PsiPackage aPackage = JavaPsiFacade.getInstance(project).findPackage(packageName);
    if (aPackage != null) {
      PsiDirectory[] directories = aPackage.getDirectories(scope);
      if (directories.length == 1) return getSourceRootDirectory(directories[0]);
    }

    final VirtualFile[] sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots();
    List<PsiDirectory> directoryList = new ArrayList<PsiDirectory>();
    for (VirtualFile sourceRoot : sourceRoots) {
      final PsiDirectory directory = PsiManager.getInstance(project).findDirectory(sourceRoot);
      directoryList.add(directory);
    }
    PsiDirectory[] sourceDirectories = directoryList.toArray(new PsiDirectory[directoryList.size()]);

    return DirectoryChooserUtil.selectDirectory(project, sourceDirectories, null, File.separatorChar + packageName.replace('.', File.separatorChar));
  }

  @Nullable
  public static PsiDirectory getRoot(Module module, String className) {
    String aPackage = extractPackage(className);
    PsiManager psiManager = PsiManager.getInstance(module.getProject());
    PsiPackage psiPackage = JavaPsiFacade.getInstance(psiManager.getProject()).findPackage(aPackage);
    if (psiPackage == null) return null;
    PsiDirectory[] directories = psiPackage.getDirectories(GlobalSearchScope.moduleScope(module));
    if (directories.length == 0) return null;

    return directories[0];
  }

  public static String extractClassName(String fqName) {
    return StringUtil.getShortName(fqName);
  }

  public static String extractPackage(String fqName) {
    int i = fqName.lastIndexOf('.');
    return i == -1 ? "" : fqName.substring(0, i);
  }

  public static String makeFQName(String aPackage, String className) {
    String fq = aPackage;
    if (!"".equals(aPackage)) {
      fq += ".";
    }
    fq += className;
    return fq;
  }

  @Nullable
  public static PsiClass createClassNamed(String newClassName, String templateName, PsiDirectory directory) throws IncorrectOperationException {
    return createClassNamed(newClassName, new Properties(FileTemplateManager.getInstance().getDefaultProperties()), templateName, directory);
  }

  @Nullable
  public static PsiClass createClassNamed(String newClassName, Map classProperties, String templateName, PsiDirectory directory)
    throws IncorrectOperationException {
    Properties defaultProperties = FileTemplateManager.getInstance().getDefaultProperties();
    Properties properties = new Properties(defaultProperties);
    properties.putAll(classProperties);

    return createClassNamed(newClassName, properties, templateName, directory);
  }

  @Nullable
  private static PsiClass createClassNamed(@Nullable String newClassName,
                                           @NotNull Properties properties,
                                           String templateName, PsiDirectory directory) throws IncorrectOperationException {
    if (newClassName == null) {
      return null;
    }
    final String className = extractClassName(newClassName);
    properties.setProperty(CLASS_NAME_PROPERTY, className);
    properties.setProperty(INTERFACE_NAME_PROPERTY, className);

    return createClassFromTemplate(properties, templateName, directory, newClassName);
  }

  @Nullable
  public static PsiClass createClassFromCustomTemplate(@Nullable PsiDirectory classDirectory, 
                                                       @Nullable final Module module,
                                                       final String className,
                                                       final String templateName) {
    if (classDirectory == null && module != null) {
      try {
        classDirectory = PackageUtil.findOrCreateDirectoryForPackage(module, "", null, false);
      }
      catch (IncorrectOperationException e) {
        return null;
      }
    }
    if (classDirectory == null) {
      return null;
    }
    try {
      final Properties properties = ApplicationManager.getApplication().isUnitTestMode() ?
                                    new Properties() :
                                    FileTemplateManager.getInstance().getDefaultProperties();
      return createClassNamed(className, new Properties(properties), templateName, classDirectory);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
  }
}
