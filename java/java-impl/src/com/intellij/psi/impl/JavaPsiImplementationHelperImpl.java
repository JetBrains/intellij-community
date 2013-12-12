/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.components.ServiceManager;
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
import com.intellij.psi.codeStyle.arrangement.MemberOrderService;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import com.intellij.psi.impl.source.codeStyle.ImportHelper;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;

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
    final Set<OrderEntry> orderEntries = new THashSet<OrderEntry>(idx.getOrderEntriesForFile(vFile));
    final String fqn = psiClass.getQualifiedName();
    if (fqn == null) return psiClass;

    PsiClass original = JavaPsiFacade.getInstance(project).findClass(fqn, new GlobalSearchScope(project) {
      @Override
      public int compare(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
        return 0;
      }

      @Override
      public boolean contains(@NotNull VirtualFile file) {
        // order for file and vFile has non empty intersection.
        List<OrderEntry> entries = idx.getOrderEntriesForFile(file);
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < entries.size(); i++) {
          final OrderEntry entry = entries.get(i);
          if (orderEntries.contains(entry)) return true;
        }
        return false;
      }

      @Override
      public boolean isSearchInModuleContent(@NotNull Module aModule) {
        return false;
      }

      @Override
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
    String relativeFilePath = packageName.isEmpty() ? sourceFileName : packageName.replace('.', '/') + '/' + sourceFileName;

    final VirtualFile vFile = clsFile.getContainingFile().getVirtualFile();
    ProjectFileIndex projectFileIndex = ProjectFileIndex.SERVICE.getInstance(clsFile.getProject());
    final Set<VirtualFile> sourceRoots = ContainerUtil.newLinkedHashSet();
    for (OrderEntry orderEntry : projectFileIndex.getOrderEntriesForFile(vFile)) {
      if (orderEntry instanceof LibraryOrSdkOrderEntry) {
        Collections.addAll(sourceRoots, orderEntry.getFiles(OrderRootType.SOURCES));
      }
    }
    for (VirtualFile root : sourceRoots) {
      VirtualFile source = root.findFileByRelativePath(relativeFilePath);
      if (source != null) {
        PsiFile psiSource = clsFile.getManager().findFile(source);
        if (psiSource instanceof PsiClassOwner) {
          return psiSource;
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
      if (relativePath == null) {
        throw new AssertionError("Null relative path: folder=" + folder + "; root=" + sourceRoot);
      }
      List<OrderEntry> orderEntries = index.getOrderEntriesForFile(virtualFile);
      if (orderEntries.isEmpty()) {
        LOG.error("Inconsistent: " + DirectoryIndex.getInstance(myProject).getInfoForDirectory(folder).toString());
      }
      final VirtualFile[] files = orderEntries.get(0).getFiles(OrderRootType.CLASSES);
      for (VirtualFile rootFile : files) {
        final VirtualFile classFile = rootFile.findFileByRelativePath(relativePath);
        if (classFile != null) {
          final PsiJavaFile javaFile = getPsiFileInRoot(classFile);
          if (javaFile != null) {
            return javaFile.getLanguageLevel();
          }
        }
      }
      return LanguageLevelProjectExtension.getInstance(myProject).getLanguageLevel();
    }
    return null;
  }

  @Nullable
  private PsiJavaFile getPsiFileInRoot(final VirtualFile dirFile) {
    final VirtualFile[] children = dirFile.getChildren();
    for (VirtualFile child : children) {
      if (StdFileTypes.CLASS.equals(child.getFileType())) {
        final PsiFile psiFile = PsiManager.getInstance(myProject).findFile(child);
        if (psiFile instanceof PsiJavaFile) return (PsiJavaFile)psiFile;
      }
    }
    return null;
  }

  @Override
  public ASTNode getDefaultImportAnchor(PsiImportList list, PsiImportStatementBase statement) {
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(list.getProject());
    ImportHelper importHelper = new ImportHelper(settings);
    return importHelper.getDefaultAnchor(list, statement);
  }

  @Nullable
  @Override
  public PsiElement getDefaultMemberAnchor(@NotNull PsiClass aClass, @NotNull PsiMember member) {
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(aClass.getProject());
    MemberOrderService service = ServiceManager.getService(MemberOrderService.class);
    PsiElement anchor = service.getAnchor(member, settings.getCommonSettings(JavaLanguage.INSTANCE), aClass);

    PsiElement newAnchor = skipWhitespaces(aClass, anchor);
    if (newAnchor != null) {
      return newAnchor;
    }

    if (anchor != null && anchor != aClass) {
      anchor = anchor.getNextSibling();
      while (anchor instanceof PsiJavaToken && (anchor.getText().equals(",") || anchor.getText().equals(";"))) {
        final boolean afterComma = anchor.getText().equals(",");
        anchor = anchor.getNextSibling();
        if (afterComma) {
          newAnchor = skipWhitespaces(aClass, anchor);
          if (newAnchor != null) return newAnchor;
        }
      }
      if (anchor != null) {
        return anchor;
      }
    }

    // The main idea is to avoid to anchor to 'white space' element because that causes reformatting algorithm
    // to perform incorrectly. The algorithm is encapsulated at the PostprocessReformattingAspect.doPostponedFormattingInner().
    final PsiElement lBrace = aClass.getLBrace();
    if (lBrace != null) {
      PsiElement result = lBrace.getNextSibling();
      while (result instanceof PsiWhiteSpace) {
        result = result.getNextSibling();
      }
      return result;
    }
    
    return aClass.getRBrace();
  }

  private static PsiElement skipWhitespaces(PsiClass aClass, PsiElement anchor) {
    if (anchor != null && PsiTreeUtil.skipSiblingsForward(anchor, PsiWhiteSpace.class) == aClass.getRBrace()) {
      // Given member should be inserted as the last child.
      return aClass.getRBrace();
    }
    return null;
  }

  // TODO remove as soon as an arrangement sub-system is provided for groovy.
  public static int getMemberOrderWeight(PsiElement member, CodeStyleSettings settings) {
    if (member instanceof PsiField) {
      if (member instanceof PsiEnumConstant) {
        return 1;
      }
      return ((PsiField)member).hasModifierProperty(PsiModifier.STATIC) ? settings.STATIC_FIELDS_ORDER_WEIGHT + 1
                                                                        : settings.FIELDS_ORDER_WEIGHT + 1;
    }
    if (member instanceof PsiMethod) {
      if (((PsiMethod)member).isConstructor()) {
        return settings.CONSTRUCTORS_ORDER_WEIGHT + 1;
      }
      return ((PsiMethod)member).hasModifierProperty(PsiModifier.STATIC) ? settings.STATIC_METHODS_ORDER_WEIGHT + 1
                                                                         : settings.METHODS_ORDER_WEIGHT + 1;
    }
    if (member instanceof PsiClass) {
      return ((PsiClass)member).hasModifierProperty(PsiModifier.STATIC) ? settings.STATIC_INNER_CLASSES_ORDER_WEIGHT + 1
                                                                        : settings.INNER_CLASSES_ORDER_WEIGHT + 1;
    }
    return -1;
  }

  @Override
  public void setupCatchBlock(@NotNull String exceptionName, @NotNull PsiType exceptionType, PsiElement context, @NotNull PsiCatchSection catchSection) {
    final FileTemplate catchBodyTemplate = FileTemplateManager.getInstance().getCodeTemplate(JavaTemplateUtil.TEMPLATE_CATCH_BODY);
    LOG.assertTrue(catchBodyTemplate != null);

    final Properties props = new Properties();
    props.setProperty(FileTemplate.ATTRIBUTE_EXCEPTION, exceptionName);
    props.setProperty(FileTemplate.ATTRIBUTE_EXCEPTION_TYPE, exceptionType.getCanonicalText());
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
