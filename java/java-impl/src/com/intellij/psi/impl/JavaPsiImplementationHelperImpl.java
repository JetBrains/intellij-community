/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.psi.impl;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.JavaTemplateUtil;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.DirectoryIndex;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import com.intellij.psi.impl.source.codeStyle.ImportHelper;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Properties;

/**
 * @author yole
 */
public class JavaPsiImplementationHelperImpl extends JavaPsiImplementationHelper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.JavaPsiImplementationHelperImpl");

  private final Project myProject;

  public JavaPsiImplementationHelperImpl(Project project) {
    myProject = project;
  }

  @Override
  public PsiClass getOriginalClass(PsiClass psiClass) {
    PsiFile psiFile = psiClass.getContainingFile();

    VirtualFile vFile = psiFile.getVirtualFile();
    final Project project = psiClass.getProject();
    final ProjectFileIndex idx = ProjectRootManager.getInstance(project).getFileIndex();

    if (vFile == null || !idx.isInLibrarySource(vFile)) return psiClass;
    final List<OrderEntry> orderEntries = idx.getOrderEntriesForFile(vFile);
    final String fqn = psiClass.getQualifiedName();
    if (fqn == null) return psiClass;

    PsiClass original = JavaPsiFacade.getInstance(project).findClass(fqn, new GlobalSearchScope(project) {
      public int compare(VirtualFile file1, VirtualFile file2) {
        return 0;
      }

      public boolean contains(VirtualFile file) {
        // order for file and vFile has non empty intersection.
        List<OrderEntry> entries = idx.getOrderEntriesForFile(file);
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < entries.size(); i++) {
          final OrderEntry entry = entries.get(i);
          if (orderEntries.contains(entry)) return true;
        }
        return false;
      }

      public boolean isSearchInModuleContent(@NotNull Module aModule) {
        return false;
      }

      public boolean isSearchInLibraries() {
        return true;
      }
    });

    return original != null ? original : psiClass;
  }

  @Override
  public PsiElement getClsFileNavigationElement(PsiJavaFile clsFile) {
    String packageName = clsFile.getPackageName();
    PsiClass[] classes = clsFile.getClasses();
    if (classes.length == 0) return clsFile;
    String sourceFileName = ((ClsClassImpl)classes[0]).getSourceFileName();
    String relativeFilePath = packageName.length() == 0 ? sourceFileName : packageName.replace('.', '/') + '/' + sourceFileName;

    final VirtualFile vFile = clsFile.getContainingFile().getVirtualFile();
    ProjectFileIndex projectFileIndex = ProjectFileIndex.SERVICE.getInstance(clsFile.getProject());
    final List<OrderEntry> orderEntries = projectFileIndex.getOrderEntriesForFile(vFile);
    for (OrderEntry orderEntry : orderEntries) {
      VirtualFile[] files = orderEntry.getFiles(OrderRootType.SOURCES);
      for (VirtualFile file : files) {
        VirtualFile source = file.findFileByRelativePath(relativeFilePath);
        if (source != null) {
          PsiFile psiSource = clsFile.getManager().findFile(source);
          if (psiSource instanceof PsiClassOwner) {
            return psiSource;
          }
        }
      }
    }
    return clsFile;
  }

  @Nullable
  @Override
  public LanguageLevel getClassesLanguageLevel(VirtualFile virtualFile) {
    final ProjectFileIndex index = ProjectRootManager.getInstance(myProject).getFileIndex();
    final VirtualFile sourceRoot = index.getSourceRootForFile(virtualFile);
    final VirtualFile folder = virtualFile.getParent();
    if (sourceRoot != null && folder != null) {
      String relativePath = VfsUtilCore.getRelativePath(folder, sourceRoot, '/');
      LOG.assertTrue(relativePath != null);
      List<OrderEntry> orderEntries = index.getOrderEntriesForFile(virtualFile);
      if (orderEntries.isEmpty()) {
        LOG.error("Inconsistent: " + DirectoryIndex.getInstance(myProject).getInfoForDirectory(folder).toString());
      }
      final VirtualFile[] files = orderEntries.get(0).getFiles(OrderRootType.CLASSES);
      for (VirtualFile rootFile : files) {
        final VirtualFile classFile = rootFile.findFileByRelativePath(relativePath);
        if (classFile != null) {
          return getLanguageLevel(classFile);
        }
      }
    }
    return null;
  }

  private LanguageLevel getLanguageLevel(final VirtualFile dirFile) {
    final VirtualFile[] children = dirFile.getChildren();
    final LanguageLevel defaultLanguageLevel = LanguageLevelProjectExtension.getInstance(myProject).getLanguageLevel();
    for (VirtualFile child : children) {
      if (StdFileTypes.CLASS.equals(child.getFileType())) {
        final PsiFile psiFile = PsiManager.getInstance(myProject).findFile(child);
        if (psiFile instanceof PsiJavaFile) return ((PsiJavaFile)psiFile).getLanguageLevel();
      }
    }

    return defaultLanguageLevel;
  }

  @Override
  public ASTNode getDefaultImportAnchor(PsiImportList list, PsiImportStatementBase statement) {
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(list.getProject());
    ImportHelper importHelper = new ImportHelper(settings);
    return importHelper.getDefaultAnchor(list, statement);
  }

  @Override
  public PsiElement getDefaultMemberAnchor(PsiClass aClass, PsiMember member) {
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(aClass.getProject());

    int order = getMemberOrderWeight(member, settings);
    if (order < 0) return null;

    PsiElement lastMember = null;
    for (PsiElement child = aClass.getFirstChild(); child != null; child = child.getNextSibling()) {
      int order1 = getMemberOrderWeight(child, settings);
      if (order1 < 0) continue;
      if (order1 > order) {
        if (lastMember != null) {
          PsiElement nextSibling = lastMember.getNextSibling();
          while (nextSibling instanceof PsiJavaToken && (nextSibling.getText().equals(",") || nextSibling.getText().equals(";"))) {
            nextSibling = nextSibling.getNextSibling();
          }
          return nextSibling == null ? aClass.getLBrace().getNextSibling() : nextSibling;
        }
        else {
          // The main idea is to avoid to anchor to 'white space' element because that causes reformatting algorithm
          // to perform incorrectly. The algorithm is encapsulated at PostprocessReformattingAspect.doPostponedFormattingInner().
          final PsiElement lBrace = aClass.getLBrace();
          if (lBrace != null) {
            PsiElement result = lBrace.getNextSibling();
            while (result instanceof PsiWhiteSpace) {
              result = result.getNextSibling();
            }
            return result;
          }
        }
      }
      lastMember = child;
    }
    return aClass.getRBrace();
  }

  public static int getMemberOrderWeight(PsiElement member, CodeStyleSettings settings) {
    if (member instanceof PsiField) {
      if (member instanceof PsiEnumConstant) {
        return 1;
      }
      else {
        return ((PsiField)member).hasModifierProperty(PsiModifier.STATIC) ? settings.STATIC_FIELDS_ORDER_WEIGHT + 1
                                                                          : settings.FIELDS_ORDER_WEIGHT + 1;
      }
    }
    else if (member instanceof PsiMethod) {
      if (((PsiMethod)member).isConstructor()) {
        return settings.CONSTRUCTORS_ORDER_WEIGHT + 1;
      }
      else {
        return ((PsiMethod)member).hasModifierProperty(PsiModifier.STATIC) ? settings.STATIC_METHODS_ORDER_WEIGHT + 1
                                                                           : settings.METHODS_ORDER_WEIGHT + 1;
      }
    }
    else if (member instanceof PsiClass) {
      return ((PsiClass)member).hasModifierProperty(PsiModifier.STATIC) ? settings.STATIC_INNER_CLASSES_ORDER_WEIGHT + 1
                                                                        : settings.INNER_CLASSES_ORDER_WEIGHT + 1;
    }
    else {
      return -1;
    }
  }

  @Override
  public void setupCatchBlock(String exceptionName, PsiElement context, PsiCatchSection catchSection) {
    final FileTemplate catchBodyTemplate = FileTemplateManager.getInstance().getCodeTemplate(JavaTemplateUtil.TEMPLATE_CATCH_BODY);
    LOG.assertTrue(catchBodyTemplate != null);

    final Properties props = new Properties();
    props.setProperty(FileTemplate.ATTRIBUTE_EXCEPTION, exceptionName);
    if (context != null && context.isPhysical()) {
      final PsiDirectory directory = context.getContainingFile().getContainingDirectory();
      if (directory != null) {
        JavaTemplateUtil.setPackageNameAttribute(props, directory);
      }
    }

    final PsiCodeBlock codeBlockFromText;
    try {
      codeBlockFromText = PsiElementFactory.SERVICE.getInstance(myProject).createCodeBlockFromText("{\n" + catchBodyTemplate.getText(props) + "\n}", null);
    }
    catch (ProcessCanceledException ce) {
      throw ce;
    }
    catch (Exception e) {
      throw new IncorrectOperationException("Incorrect file template", e);
    }
    catchSection.getCatchBlock().replace(codeBlockFromText);
  }
}
