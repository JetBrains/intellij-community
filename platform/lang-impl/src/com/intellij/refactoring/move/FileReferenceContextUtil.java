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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceOwner;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;

import java.util.Map;

public class FileReferenceContextUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.move.FileReferenceContextUtil");
  private static final Key<PsiFileSystemItem> REF_FILE_SYSTEM_ITEM_KEY = Key.create("REF_FILE_SYSTEM_ITEM_KEY");

  private FileReferenceContextUtil() {
  }

  public static Map<String, PsiFileSystemItem> encodeFileReferences(PsiElement element) {
    final Map<String,PsiFileSystemItem> map = new HashMap<String, PsiFileSystemItem>();
    element.accept(new PsiRecursiveElementWalkingVisitor(true) {
      @Override public void visitElement(PsiElement element) {
        final PsiReference[] refs = element.getReferences();
        if (refs.length > 0 && refs[0] instanceof FileReferenceOwner) {
          final FileReference ref = ((FileReferenceOwner)refs[0]).getLastFileReference();
          if (ref != null) {
            final ResolveResult[] results = ref.multiResolve(false);
            for (ResolveResult result : results) {
              if (result.getElement() instanceof PsiFileSystemItem) {
                PsiFileSystemItem fileSystemItem = (PsiFileSystemItem)result.getElement();
                element.putCopyableUserData(REF_FILE_SYSTEM_ITEM_KEY, fileSystemItem);
                map.put(element.getText(), fileSystemItem);
                break;
              }
            }
          }
        }
        super.visitElement(element);
      }
    });
    return map;
  }

  public static void decodeFileReferences(PsiElement element) {
    element.accept(new PsiRecursiveElementVisitor(true) {
      @Override public void visitElement(PsiElement element) {
        final PsiFileSystemItem item = element.getCopyableUserData(REF_FILE_SYSTEM_ITEM_KEY);
        element.putCopyableUserData(REF_FILE_SYSTEM_ITEM_KEY, null);
        element = bindElement(element, item);
        if (element != null) {
          element.acceptChildren(this);
        }
      }
    });
  }

  public static void decodeFileReferences(PsiElement element, final Map<String, PsiFileSystemItem> map, final TextRange range) {
    element.accept(new PsiRecursiveElementVisitor(true) {
      @Override public void visitElement(PsiElement element) {
        if (!range.intersects(element.getTextRange())) return;
        String text = element.getText();
        PsiFileSystemItem item = map.get(text);
        element = bindElement(element, item);
        if (element != null) {
          element.acceptChildren(this);
        }
      }
    });
  }

  private static PsiElement bindElement(PsiElement element, PsiFileSystemItem item) {
    if (item != null && item.isValid()) {
      PsiReference[] refs = element.getReferences();
      if (refs.length > 0 && refs[0] instanceof FileReferenceOwner) {
        final FileReference ref = ((FileReferenceOwner)refs[0]).getLastFileReference();
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
    return element;
  }
}
