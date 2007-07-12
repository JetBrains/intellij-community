/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ClassUtil {
  private ClassUtil() {}

  public static String extractPackageName(String className) {
    if (className != null) {
      int i = className.lastIndexOf('.');
      return i == -1 ? "" : className.substring(0, i);                                

    }
    return null;
  }

  public static String extractClassName(@NotNull String fqName) {
    int i = fqName.lastIndexOf('.');
    return i == -1 ? fqName : fqName.substring(i + 1);
  }

  public static String createNewClassQualifiedName(String qualifiedName, String className) {
    if (className == null){
      return null;
    }
    if (qualifiedName == null || qualifiedName.length() == 0){
      return className;
    }
    return qualifiedName + "." + extractClassName(className);
  }

  public static PsiDirectory sourceRoot(PsiDirectory containingDirectory) {
    while (containingDirectory != null) {
      if (containingDirectory.isSourceRoot()) {
        return containingDirectory;
      }
      containingDirectory = containingDirectory.getParentDirectory();
    }
    return null;
  }

  public static void formatClassName(@NotNull final PsiClass aClass, final StringBuilder buf) {
    final String qName = aClass.getQualifiedName();
    if (qName != null) {
      buf.append(qName);
    }
    else {
      final PsiClass parentClass = PsiTreeUtil.getParentOfType(aClass, PsiClass.class);
      if (parentClass != null) {
        formatClassName(parentClass, buf);
        buf.append("$");
        buf.append(getNonQualifiedClassIdx(aClass));
        final String name = aClass.getName();
        if (name != null) {
          buf.append(name);
        }
      }
    }
  }

  public static int getNonQualifiedClassIdx(@NotNull final PsiClass psiClass) {
    final int[] result = new int[]{-1};
    final PsiClass containingClass = PsiTreeUtil.getParentOfType(psiClass, PsiClass.class);
    if (containingClass != null) {
      containingClass.accept(new PsiRecursiveElementVisitor() {
        private int myCurrentIdx = 0;

        public void visitElement(PsiElement element) {
          if (result[0] == -1) {
            super.visitElement(element);
          }
        }

        public void visitClass(PsiClass aClass) {
          super.visitClass(aClass);
          if (aClass.getQualifiedName() == null) {
            myCurrentIdx++;
            if (psiClass == aClass) {
              result[0] = myCurrentIdx;
            }
          }
        }
      });
    }
    return result[0];
  }

  public static PsiClass findNonQualifiedClassByIndex(final String indexName, @NotNull final PsiClass contaningClass) {
    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      for (int i = 0; i < indexName.length(); i++) {
        final char c = indexName.charAt(i);
        if (Character.isDigit(c)) {
          builder.append(c);
        }
        else {
          break;
        }
      }
      final int idx = Integer.parseInt(builder.toString());
      final String name = builder.length() < indexName.length() ? indexName.substring(builder.length()) : null;
      final PsiClass[] result = new PsiClass[1];
      contaningClass.accept(new PsiRecursiveElementVisitor() {
        private int myCurrentIdx = 0;

        public void visitElement(PsiElement element) {
          if (result[0] == null) {
            super.visitElement(element);
          }
        }

        public void visitClass(PsiClass aClass) {
          super.visitClass(aClass);
          if (aClass.getQualifiedName() == null) {
            myCurrentIdx++;
            if (myCurrentIdx == idx && Comparing.strEqual(name, aClass.getName())) {
              result[0] = aClass;
            }
          }
        }
      });
      return result[0];
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }


  /**
   * Finds anonymous classes. Uses javac notation.
   * @param psiManager project to search
   * @param externalName class qualified name
   * @return found psiClass
   */
  @Nullable
  public static PsiClass findPsiClass(final PsiManager psiManager, String externalName){
    return findPsiClass(psiManager, externalName, null);
  }

  @Nullable
  private static PsiClass findPsiClass(final PsiManager psiManager, String externalName, PsiClass psiClass) {
    final int topIdx = externalName.indexOf('$');
    if (topIdx > -1) {
      if (psiClass == null) {
        psiClass = psiManager.findClass(externalName.substring(0, topIdx), GlobalSearchScope.allScope(psiManager.getProject()));
      }
      if (psiClass == null) return null;
      externalName = externalName.substring(topIdx + 1);
      final int nextIdx = externalName.indexOf("$");
      if (nextIdx > -1) {
        return findPsiClass(psiManager, externalName.substring(nextIdx), findNonQualifiedClassByIndex(externalName.substring(0, nextIdx), psiClass));
      } else {
        return findNonQualifiedClassByIndex(externalName, psiClass);
      }
    } else {
      return psiManager.findClass(externalName, GlobalSearchScope.allScope(psiManager.getProject()));
    }
  }
}