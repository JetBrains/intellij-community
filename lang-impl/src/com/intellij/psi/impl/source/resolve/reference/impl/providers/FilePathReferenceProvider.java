package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.*;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.patterns.MatchingContext;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author cdr
 */
public class FilePathReferenceProvider extends PsiReferenceProvider {
  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element, String text, int offset, final boolean soft) {
    return new FileReferenceSet(text, element, offset, this, true) {
      protected boolean isSoft() {
        return soft;
      }

      public boolean isAbsolutePathReference() {
        return true;
      }

      @NotNull public Collection<PsiFileSystemItem> computeDefaultContexts() {
        final Module module = ModuleUtil.findModuleForPsiElement(getElement());
        return getRoots(module, true);
      }

      public FileReference createFileReference(final TextRange range, final int index, final String text) {
        return FilePathReferenceProvider.this.createFileReference(this, range, index, text);
      }

      @Override
      protected Condition<PsiElement> createCondition() {
        return new Condition<PsiElement>() {
          public boolean value(final PsiElement element) {
            return isPsiElementAccepted(element);
          }
        };
      }
    }.getAllReferences();

  }

  protected boolean isPsiElementAccepted(PsiElement element) {
    return !(element instanceof PsiJavaFile && element instanceof PsiCompiledElement);
  }

  protected FileReference createFileReference(FileReferenceSet referenceSet, final TextRange range, final int index, final String text) {
    return new FileReference(referenceSet, range, index, text);
  }

  @NotNull
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull final MatchingContext matchingContext) {
    String text = null;
    if (element instanceof PsiLiteralExpression) {
      Object value = ((PsiLiteralExpression)element).getValue();
      if (value instanceof String) {
        text = (String)value;
      }
    }
    //else if (element instanceof XmlAttributeValue) {
    //  text = ((XmlAttributeValue)element).getValue();
    //}
    if (text == null) return PsiReference.EMPTY_ARRAY;
    return getReferencesByElement(element, text, 1, true);
  }

  @NotNull
  public static Collection<PsiFileSystemItem> getRoots(final Module thisModule, boolean includingClasses) {
    if (thisModule == null) return Collections.emptyList();
    List<Module> modules = new ArrayList<Module>();
    modules.add(thisModule);
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(thisModule);
    modules.addAll(Arrays.asList(moduleRootManager.getDependencies()));

    List<PsiFileSystemItem> result = new ArrayList<PsiFileSystemItem>();
    final PsiManager psiManager = PsiManager.getInstance(thisModule.getProject());
    if (includingClasses) {
      String[] libraryUrls = moduleRootManager.getUrls(OrderRootType.CLASSES);
      for (String libraryUrl : libraryUrls) {
        VirtualFile libFile = VirtualFileManager.getInstance().findFileByUrl(libraryUrl);
        if (libFile != null) {
          PsiDirectory directory = psiManager.findDirectory(libFile);
          if (directory != null) {
            result.add(directory);
          }
        }
      }
    }

    for (Module module : modules) {
      moduleRootManager = ModuleRootManager.getInstance(module);
      VirtualFile[] sourceRoots = moduleRootManager.getSourceRoots();
      for (VirtualFile root : sourceRoots) {
        final PsiDirectory directory = psiManager.findDirectory(root);
        if (directory != null) {
          final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);
          if (aPackage != null && aPackage.getName() != null) {
            // package prefix
            result.add(PackagePrefixFileSystemItem.create(directory));
          }
          else {
            result.add(directory);
          }
        }
      }
    }
    return result;
  }

}
