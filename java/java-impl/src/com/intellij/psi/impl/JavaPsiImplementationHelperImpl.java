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
package com.intellij.psi.impl;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.JavaTemplateUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.EffectiveLanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.DirectoryIndex;
import com.intellij.openapi.roots.impl.LibraryScopeCache;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.arrangement.MemberOrderService;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import com.intellij.psi.impl.compiled.ClsElementImpl;
import com.intellij.psi.impl.source.codeStyle.ImportHelper;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    PsiCompiledElement cls = psiClass.getUserData(ClsElementImpl.COMPILED_ELEMENT);
    if (cls != null && cls.isValid()) return (PsiClass)cls;
    
    if (DumbService.isDumb(myProject)) return psiClass;

    VirtualFile vFile = psiClass.getContainingFile().getVirtualFile();
    final ProjectFileIndex idx = ProjectRootManager.getInstance(myProject).getFileIndex();
    if (vFile == null || !idx.isInLibrarySource(vFile)) return psiClass;

    String fqn = psiClass.getQualifiedName();
    if (fqn == null) return psiClass;

    final Set<OrderEntry> orderEntries = ContainerUtil.newHashSet(idx.getOrderEntriesForFile(vFile));
    GlobalSearchScope librariesScope = LibraryScopeCache.getInstance(myProject).getLibrariesOnlyScope();
    for (PsiClass original : JavaPsiFacade.getInstance(myProject).findClasses(fqn, librariesScope)) {
      PsiFile psiFile = original.getContainingFile();
      if (psiFile != null) {
        VirtualFile candidateFile = psiFile.getVirtualFile();
        if (candidateFile != null) {
          // order for file and vFile has non empty intersection.
          List<OrderEntry> entries = idx.getOrderEntriesForFile(candidateFile);
          //noinspection ForLoopReplaceableByForEach
          for (int i = 0; i < entries.size(); i++) {
            if (orderEntries.contains(entries.get(i))) return original;
          }
        }
      }
    }

    return psiClass;
  }

  @NotNull
  @Override
  public PsiElement getClsFileNavigationElement(PsiJavaFile clsFile) {
    PsiClass[] classes = clsFile.getClasses();
    if (classes.length == 0) return clsFile;

    String sourceFileName = ((ClsClassImpl)classes[0]).getSourceFileName();
    String packageName = clsFile.getPackageName();
    String relativePath = packageName.isEmpty() ? sourceFileName : packageName.replace('.', '/') + '/' + sourceFileName;

    ProjectFileIndex index = ProjectFileIndex.SERVICE.getInstance(clsFile.getProject());
    for (OrderEntry orderEntry : index.getOrderEntriesForFile(clsFile.getContainingFile().getVirtualFile())) {
      if (!(orderEntry instanceof LibraryOrSdkOrderEntry)) continue;
      for (VirtualFile root : orderEntry.getFiles(OrderRootType.SOURCES)) {
        VirtualFile source = root.findFileByRelativePath(relativePath);
        if (source != null && source.isValid()) {
          PsiFile psiSource = clsFile.getManager().findFile(source);
          if (psiSource instanceof PsiClassOwner) {
            return psiSource;
          }
        }
      }
    }

    return clsFile;
  }

  @NotNull
  @Override
  public LanguageLevel getEffectiveLanguageLevel(@Nullable VirtualFile virtualFile) {
    if (virtualFile == null) return PsiUtil.getLanguageLevel(myProject);

    final VirtualFile folder = virtualFile.getParent();
    if (folder != null) {
      final LanguageLevel level = folder.getUserData(LanguageLevel.KEY);
      if (level != null) return level;
    }

    final ProjectFileIndex index = ProjectRootManager.getInstance(myProject).getFileIndex();
    Module module = index.getModuleForFile(virtualFile);
    if (module != null && index.isInSourceContent(virtualFile)) {
      return EffectiveLanguageLevelUtil.getEffectiveLanguageLevel(module);
    }

    LanguageLevel classesLanguageLevel = getClassesLanguageLevel(virtualFile);
    return classesLanguageLevel != null ? classesLanguageLevel : PsiUtil.getLanguageLevel(myProject);
  }

  /**
   * For files under a library source root, returns the language level configured for the corresponding classes root.
   *
   * @param virtualFile virtual file for which language level is requested.
   * @return language level for classes root or null if file is not under a library source root or no matching classes root is found.
   */
  @Nullable
  private LanguageLevel getClassesLanguageLevel(VirtualFile virtualFile) {
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
        LOG.error("Inconsistent: " + DirectoryIndex.getInstance(myProject).getInfoForFile(folder).toString());
      }
      final String className = virtualFile.getNameWithoutExtension();
      final VirtualFile[] files = orderEntries.get(0).getFiles(OrderRootType.CLASSES);
      for (VirtualFile rootFile : files) {
        final VirtualFile classFile = rootFile.findFileByRelativePath(relativePath);
        if (classFile != null) {
          final PsiJavaFile javaFile = getPsiFileInRoot(classFile, className);
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
  private PsiJavaFile getPsiFileInRoot(final VirtualFile dirFile, @Nullable String className) {
    if (className != null) {
      final VirtualFile classFile = dirFile.findChild(StringUtil.getQualifiedName(className, StdFileTypes.CLASS.getDefaultExtension()));
      if (classFile != null) {
        final PsiFile psiFile = PsiManager.getInstance(myProject).findFile(classFile);
        if (psiFile instanceof PsiJavaFile) {
          return (PsiJavaFile)psiFile;
        }
      }
    }

    final VirtualFile[] children = dirFile.getChildren();
    for (VirtualFile child : children) {
      if (StdFileTypes.CLASS.equals(child.getFileType()) && child.isValid()) {
        final PsiFile psiFile = PsiManager.getInstance(myProject).findFile(child);
        if (psiFile instanceof PsiJavaFile) {
          return (PsiJavaFile)psiFile;
        }
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

  @Override
  public void setupCatchBlock(@NotNull String exceptionName, @NotNull PsiType exceptionType, PsiElement context, @NotNull PsiCatchSection catchSection) {
    final FileTemplate catchBodyTemplate = FileTemplateManager.getInstance(catchSection.getProject()).getCodeTemplate(JavaTemplateUtil.TEMPLATE_CATCH_BODY);
    LOG.assertTrue(catchBodyTemplate != null);

    Properties props = FileTemplateManager.getInstance(myProject).getDefaultProperties();
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
      throw new IncorrectOperationException("Incorrect file template", (Throwable)e);
    }
    catchSection.getCatchBlock().replace(codeBlockFromText);
  }
}
