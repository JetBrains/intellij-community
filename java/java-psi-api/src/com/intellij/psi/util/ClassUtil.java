/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ClassUtil {
  private ClassUtil() { }

  public static String extractPackageName(String className) {
    if (className != null) {
      int i = className.lastIndexOf('.');
      return i == -1 ? "" : className.substring(0, i);
    }
    return null;
  }

  @NotNull
  public static String extractClassName(@NotNull String fqName) {
    int i = fqName.lastIndexOf('.');
    return i == -1 ? fqName : fqName.substring(i + 1);
  }

  public static String createNewClassQualifiedName(String qualifiedName, String className) {
    if (className == null) {
      return null;
    }
    if (qualifiedName == null || qualifiedName.isEmpty()) {
      return className;
    }
    return qualifiedName + "." + extractClassName(className);
  }

  public static PsiDirectory sourceRoot(PsiDirectory containingDirectory) {
    while (containingDirectory != null) {
      if (JavaDirectoryService.getInstance().isSourceRoot(containingDirectory)) {
        return containingDirectory;
      }
      containingDirectory = containingDirectory.getParentDirectory();
    }
    return null;
  }

  public static void formatClassName(@NotNull final PsiClass aClass, @NotNull StringBuilder buf) {
    final String qName = aClass.getQualifiedName();
    if (qName != null) {
      buf.append(qName);
    }
    else {
      final PsiClass parentClass = PsiTreeUtil.getContextOfType(aClass, PsiClass.class, true);
      if (parentClass != null) {
        formatClassName(parentClass, buf);
        buf.append("$");
        buf.append(getNonQualifiedClassIdx(aClass, parentClass));
        final String name = aClass.getName();
        if (name != null) {
          buf.append(name);
        }
      }
    }
  }

  private static int getNonQualifiedClassIdx(@NotNull final PsiClass psiClass, @NotNull final PsiClass containingClass) {
    TObjectIntHashMap<PsiClass> indices =
      CachedValuesManager.getCachedValue(containingClass, () -> {
        final TObjectIntHashMap<PsiClass> map = new TObjectIntHashMap<>();
        int index = 0;
        for (PsiClass aClass : SyntaxTraverser.psiTraverser().withRoot(containingClass).postOrderDfsTraversal().filter(PsiClass.class)) {
          if (aClass.getQualifiedName() == null) {
            map.put(aClass, ++index);
          }
        }
        return CachedValueProvider.Result.create(map, containingClass);
      });

    return indices.get(psiClass);
  }

  public static PsiClass findNonQualifiedClassByIndex(@NotNull String indexName, @NotNull PsiClass containingClass) {
    return findNonQualifiedClassByIndex(indexName, containingClass, false);
  }

  public static PsiClass findNonQualifiedClassByIndex(@NotNull String indexName,
                                                      @NotNull final PsiClass containingClass,
                                                      final boolean jvmCompatible) {
    String prefix = getDigitPrefix(indexName);
    final int idx = !prefix.isEmpty() ? Integer.parseInt(prefix) : -1;
    final String name = prefix.length() < indexName.length() ? indexName.substring(prefix.length()) : null;
    final PsiClass[] result = new PsiClass[1];
    containingClass.accept(new JavaRecursiveElementVisitor() {
      private int myCurrentIdx;

      @Override
      public void visitElement(PsiElement element) {
        if (result[0] == null) {
          super.visitElement(element);
        }
      }

      @Override
      public void visitClass(PsiClass aClass) {
        if (!jvmCompatible) {
          super.visitClass(aClass);
          if (aClass.getQualifiedName() == null) {
            myCurrentIdx++;
            if (myCurrentIdx == idx && Comparing.strEqual(name, aClass.getName())) {
              result[0] = aClass;
            }
          }
          return;
        }
        if (aClass == containingClass) {
          super.visitClass(aClass);
          return;
        }
        if (Comparing.strEqual(name, aClass.getName())) {
          myCurrentIdx++;
          if (myCurrentIdx == idx || idx == -1) {
            result[0] = aClass;
          }
        }
      }

      @Override
      public void visitTypeParameter(final PsiTypeParameter classParameter) {
        if (!jvmCompatible) {
          super.visitTypeParameter(classParameter);
        }
        else {
          visitElement(classParameter);
        }
      }
    });
    return result[0];
  }

  @NotNull
  private static String getDigitPrefix(@NotNull String indexName) {
    int i;
    for (i = 0; i < indexName.length(); i++) {
      final char c = indexName.charAt(i);
      if (!Character.isDigit(c)) {
        break;
      }
    }
    return i == 0 ? "" : indexName.substring(0, i);
  }

  /**
   * Looks for inner and anonymous classes by FQN in a javac notation ('pkg.Top$Inner').
   */
  @Nullable
  public static PsiClass findPsiClass(@NotNull PsiManager manager, @NotNull String name) {
    return findPsiClass(manager, name, null, false);
  }

  @Nullable
  public static PsiClass findPsiClass(@NotNull PsiManager manager,
                                      @NotNull String name,
                                      @Nullable PsiClass parent,
                                      boolean jvmCompatible) {
    GlobalSearchScope scope = GlobalSearchScope.allScope(manager.getProject());
    return findPsiClass(manager, name, parent, jvmCompatible, scope);
  }

  @Nullable
  public static PsiClass findPsiClass(@NotNull PsiManager manager,
                                      @NotNull String name,
                                      @Nullable PsiClass parent,
                                      boolean jvmCompatible,
                                      @NotNull GlobalSearchScope scope) {
    if (parent != null) {
      return findSubClass(name, parent, jvmCompatible);
    }

    PsiClass result = JavaPsiFacade.getInstance(manager.getProject()).findClass(name, scope);
    if (result != null) return result;

    int p = 0;
    while ((p = name.indexOf('$', p + 1)) > 0 && p < name.length() - 1) {
      String prefix = name.substring(0, p);
      parent = JavaPsiFacade.getInstance(manager.getProject()).findClass(prefix, scope);
      if (parent != null) {
        String suffix = name.substring(p + 1);
        result = findSubClass(suffix, parent, jvmCompatible);
        if (result != null) return result;
      }
    }

    return null;
  }

  @Nullable
  private static PsiClass findSubClass(@NotNull String name, @NotNull PsiClass parent, boolean jvmCompatible) {
    PsiClass result = isIndexed(name) ? findNonQualifiedClassByIndex(name, parent, jvmCompatible) : parent.findInnerClassByName(name, false);
    if (result != null) return result;

    int p = 0;
    while ((p = name.indexOf('$', p + 1)) > 0 && p < name.length() - 1) {
      String prefix = name.substring(0, p);
      PsiClass subClass = isIndexed(prefix) ? findNonQualifiedClassByIndex(prefix, parent, jvmCompatible) : parent.findInnerClassByName(prefix, false);
      if (subClass != null) {
        String suffix = name.substring(p + 1);
        result = findSubClass(suffix, subClass, jvmCompatible);
        if (result != null) return result;
      }
    }

    return null;
  }

  private static boolean isIndexed(String name) {
    return Character.isDigit(name.charAt(0));
  }

  @Nullable
  public static String getJVMClassName(@NotNull PsiClass aClass) {
    final PsiClass containingClass = aClass.getContainingClass();
    if (containingClass != null) {
      String parentName = getJVMClassName(containingClass);
      if (parentName == null) {
        return null;
      }
      return parentName + "$" + aClass.getName();
    }
    return aClass.getQualifiedName();
  }

  /**
   * Looks for inner and anonymous classes by internal name ('pkg/Top$Inner').
   */
  @Nullable
  public static PsiClass findPsiClassByJVMName(@NotNull PsiManager manager, @NotNull String jvmClassName) {
    return findPsiClass(manager, jvmClassName.replace('/', '.'), null, true);
  }

  public static boolean isTopLevelClass(@NotNull PsiClass aClass) {
    if (aClass.getContainingClass() != null) {
      return false;
    }

    if (aClass instanceof PsiAnonymousClass) {
      return false;
    }

    PsiElement parent = aClass.getParent();
    if (parent instanceof PsiDeclarationStatement && parent.getParent() instanceof PsiCodeBlock) {
      return false;
    }

    PsiFile parentFile = aClass.getContainingFile();
    return parentFile != null && parentFile.getLanguage() == JavaLanguage.INSTANCE;  // do not select JspClass
  }

  public static String getVMParametersMethodSignature(PsiMethod method) {
    return StringUtil.join(method.getParameterList().getParameters(),
                           param -> {
                             PsiType type = TypeConversionUtil.erasure(param.getType());
                             return type != null ? type.accept(createSignatureVisitor()) : "";
                           },
                           ",");
  }

  private static PsiTypeVisitor<String> createSignatureVisitor() {
    return new PsiTypeVisitor<String>() {
      @Override
      public String visitPrimitiveType(PsiPrimitiveType primitiveType) {
        return primitiveType.getCanonicalText();
      }

      @Override
      public String visitClassType(PsiClassType classType) {
        PsiClass aClass = classType.resolve();
        if (aClass == null) {
          return "";
        }
        return getJVMClassName(aClass);
      }

      @Override
      public String visitArrayType(PsiArrayType arrayType) {
        PsiType componentType = arrayType.getComponentType();
        String typePresentation = componentType.accept(this);
        if (componentType instanceof PsiClassType) {
          typePresentation = "L" + typePresentation + ";";
        }
        return "[" + typePresentation;
      }
    };
  }
}