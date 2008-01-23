package com.intellij.refactoring.move;

import com.intellij.psi.*;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.IncorrectOperationException;

public class FileReferenceContextUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.move.FileReferenceContextUtil");
  public static final Key<PsiFileSystemItem> REF_FILE_SYSTEM_ITEM_KEY = Key.create("REF_FILE_SYSTEM_ITEM_KEY");

  private FileReferenceContextUtil() {
  }

  public static void encodeFileReferences(PsiElement element) {
    element.accept(new PsiRecursiveElementVisitor(true) {
      @Override public void visitElement(PsiElement element) {
        final PsiReference[] refs = element.getReferences();
        if (refs.length > 0) {
          final PsiReference ref = refs[refs.length - 1];
          if (ref instanceof PsiPolyVariantReference) {
            final ResolveResult[] results = ((PsiPolyVariantReference)ref).multiResolve(false);
            for (ResolveResult result : results) {
              if (result.getElement() instanceof PsiFileSystemItem) {
                element.putCopyableUserData(REF_FILE_SYSTEM_ITEM_KEY, ((PsiFileSystemItem)result.getElement()));
                break;
              }
            }
          } else {
          final PsiElement resolved = ref.resolve();
            if (resolved instanceof PsiFileSystemItem) {
              element.putCopyableUserData(REF_FILE_SYSTEM_ITEM_KEY, ((PsiFileSystemItem)resolved));
            }
          }
        }
        element.acceptChildren(this);
      }
    });
  }

  public static void decodeFileReferences(PsiElement element) {
    final PsiFile file = element.getContainingFile();

    element.accept(new PsiRecursiveElementVisitor(true) {
      @Override public void visitElement(PsiElement element) {
        final PsiFileSystemItem item = element.getCopyableUserData(REF_FILE_SYSTEM_ITEM_KEY);
        element.putCopyableUserData(REF_FILE_SYSTEM_ITEM_KEY, null);
        if (item != null && item.isValid()) {
          PsiReference[] refs = element.getReferences();
          if (refs.length > 0) {
            try {
              element = refs[refs.length - 1].bindToElement(item);
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        }
        if (element != null) {
          element.acceptChildren(this);
        }
      }
    });
  }
}
