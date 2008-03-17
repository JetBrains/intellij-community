package com.intellij.refactoring.move;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.util.IncorrectOperationException;

public class FileReferenceContextUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.move.FileReferenceContextUtil");
  private static final Key<PsiFileSystemItem> REF_FILE_SYSTEM_ITEM_KEY = Key.create("REF_FILE_SYSTEM_ITEM_KEY");

  private FileReferenceContextUtil() {
  }

  public static void encodeFileReferences(PsiElement element) {
    element.accept(new PsiRecursiveElementVisitor(true) {
      @Override public void visitElement(PsiElement element) {
        final PsiReference[] refs = element.getReferences();
        if (refs.length > 0 && refs[0] instanceof FileReference) {
          final FileReference ref = ((FileReference)refs[0]).getFileReferenceSet().getLastReference();
          if (ref != null) {
            final ResolveResult[] results = ref.multiResolve(false);
            for (ResolveResult result : results) {
              if (result.getElement() instanceof PsiFileSystemItem) {
                element.putCopyableUserData(REF_FILE_SYSTEM_ITEM_KEY, ((PsiFileSystemItem)result.getElement()));
                break;
              }
            }
          }
        }
        element.acceptChildren(this);
      }
    });
  }

  public static void decodeFileReferences(PsiElement element) {

    element.accept(new PsiRecursiveElementVisitor(true) {
      @Override public void visitElement(PsiElement element) {
        final PsiFileSystemItem item = element.getCopyableUserData(REF_FILE_SYSTEM_ITEM_KEY);
        element.putCopyableUserData(REF_FILE_SYSTEM_ITEM_KEY, null);
        if (item != null && item.isValid()) {
          PsiReference[] refs = element.getReferences();
          if (refs.length > 0 && refs[0] instanceof FileReference) {
            final FileReference ref = ((FileReference)refs[0]).getFileReferenceSet().getLastReference();
            if (ref != null) {
              try {
                element = ref.bindToElement(item);
              }
              catch (IncorrectOperationException e) {
                LOG.error(e);
              }
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
