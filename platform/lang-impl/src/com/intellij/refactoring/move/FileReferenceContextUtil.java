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

package com.intellij.refactoring.move;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceOwner;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.PsiFileReference;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;

import java.util.Map;

public class FileReferenceContextUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.move.FileReferenceContextUtil");
  private static final Key<PsiFileSystemItem> REF_FILE_SYSTEM_ITEM_KEY = Key.create("REF_FILE_SYSTEM_ITEM_KEY");

  private FileReferenceContextUtil() {
  }

  public static Map<String, PsiFileSystemItem> encodeFileReferences(PsiElement element) {
    final Map<String,PsiFileSystemItem> map = new HashMap<>();
    if (element == null || element instanceof PsiCompiledElement || isBinary(element)) return map;
    element.accept(new PsiRecursiveElementWalkingVisitor(true) {
      @Override public void visitElement(PsiElement element) {
        if (element instanceof PsiLanguageInjectionHost && element.isValid()) {
          InjectedLanguageManager.getInstance(element.getProject()).enumerate(element, (injectedPsi, places) -> encodeFileReferences(injectedPsi));
        }

        final PsiReference[] refs = element.getReferences();
        for (PsiReference reference : refs) {
          final PsiFileReference ref = reference instanceof FileReferenceOwner ?
                                       ((FileReferenceOwner)reference).getLastFileReference() :
                                       null;
          if (ref != null && encodeFileReference(element, ref, map)) break;
        }
        super.visitElement(element);
      }
    });
    return map;
  }

  private static boolean encodeFileReference(PsiElement element, PsiFileReference ref, Map<String, PsiFileSystemItem> map) {
    final ResolveResult[] results = ref.multiResolve(false);
    for (ResolveResult result : results) {
      if (result.getElement() instanceof PsiFileSystemItem) {
        PsiFileSystemItem fileSystemItem = (PsiFileSystemItem)result.getElement();
        element.putCopyableUserData(REF_FILE_SYSTEM_ITEM_KEY, fileSystemItem);
        map.put(element.getText(), fileSystemItem);
        return true;
      }
    }
    return false;
  }

  private static boolean isBinary(PsiElement element) {
    final PsiFile containingFile = element.getContainingFile();
    if (containingFile == null || containingFile.getFileType().isBinary()) return true;
    return false;
  }

  public static void decodeFileReferences(PsiElement element) {
    if (element == null || element instanceof PsiCompiledElement || isBinary(element)) return;
    element.accept(new PsiRecursiveElementVisitor(true) {
      @Override public void visitElement(PsiElement element) {
        final PsiFileSystemItem item = element.getCopyableUserData(REF_FILE_SYSTEM_ITEM_KEY);
        element.putCopyableUserData(REF_FILE_SYSTEM_ITEM_KEY, null);
        element = bindElement(element, item);
        if (element != null) {
          element.acceptChildren(this);
        }

        if (element instanceof PsiLanguageInjectionHost) {
          InjectedLanguageManager.getInstance(element.getProject()).enumerate(element, (injectedPsi, places) -> decodeFileReferences(injectedPsi));
        }
      }
    });
  }

  public static void decodeFileReferences(PsiElement element, final Map<String, PsiFileSystemItem> map, final TextRange range) {
    if (element == null || element instanceof PsiCompiledElement || isBinary(element)) return;
    element.accept(new PsiRecursiveElementVisitor(true) {
      @Override public void visitElement(PsiElement element) {
        if (!range.intersects(element.getTextRange())) return;
        String text = element.getText();
        PsiFileSystemItem item = map.get(text);
        element.putCopyableUserData(REF_FILE_SYSTEM_ITEM_KEY, item);
        element.acceptChildren(this);
      }
    });
    decodeFileReferences(element);
  }

  private static PsiElement bindElement(final PsiElement element, PsiFileSystemItem item) {
    if (item != null && item.isValid() && item.getVirtualFile() != null) {
      PsiReference[] refs = element.getReferences();
      for (PsiReference ref : refs) {
        if (ref instanceof FileReferenceOwner) {
          final PsiFileReference fileReference = ((FileReferenceOwner)ref).getLastFileReference();
          if (fileReference != null) {
            try {
              PsiElement newElement = fileReference.bindToElement(item);
              if (newElement != null) {
                // assertion for troubles like IDEA-59540
                LOG.assertTrue(element.getClass() == newElement.getClass(), "Reference " + ref + " violated contract of bindToElement()");
              }
              return newElement;
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
          break;
        }
      }
    }
    return element;
  }
}
