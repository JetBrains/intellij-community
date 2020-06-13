// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public final class ClassUtil {
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
    Object2IntMap<PsiClass> indices =
      CachedValuesManager.getCachedValue(containingClass, () -> {
        Object2IntOpenHashMap<PsiClass> map = new Object2IntOpenHashMap<>();
        int index = 0;
        for (PsiClass aClass : SyntaxTraverser.psiTraverser().withRoot(containingClass).postOrderDfsTraversal().filter(PsiClass.class)) {
          if (aClass.getQualifiedName() == null) {
            map.put(aClass, ++index);
          }
        }
        return CachedValueProvider.Result.create(map, containingClass);
      });

    return indices.getInt(psiClass);
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
      public void visitElement(@NotNull PsiElement element) {
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

  public static String getAsmMethodSignature(PsiMethod method) {
    StringBuilder signature = new StringBuilder();
    signature.append("(");
    for (PsiParameter param : method.getParameterList().getParameters()) {
      signature.append(getBinaryPresentation(param.getType()));
    }
    signature.append(")");
    signature.append(getBinaryPresentation(Optional.ofNullable(method.getReturnType()).orElse(PsiType.VOID)));
    return signature.toString();
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
      public String visitPrimitiveType(@NotNull PsiPrimitiveType primitiveType) {
        return primitiveType.getCanonicalText();
      }

      @Override
      public String visitClassType(@NotNull PsiClassType classType) {
        PsiClass aClass = classType.resolve();
        if (aClass == null) {
          return "";
        }
        return getJVMClassName(aClass);
      }

      @Override
      public String visitArrayType(@NotNull PsiArrayType arrayType) {
        PsiType componentType = arrayType.getComponentType();
        String typePresentation = componentType.accept(this);
        if (arrayType.getDeepComponentType() instanceof PsiPrimitiveType) {
          return typePresentation + "[]";
        }
        if (componentType instanceof PsiClassType) {
          typePresentation = "L" + typePresentation + ";";
        }
        return "[" + typePresentation;
      }
    };
  }

  @NotNull
  public static String getClassObjectPresentation(@NotNull PsiType psiType) {
     return toBinary(psiType, false);
  }

  @NotNull
  public static String getBinaryPresentation(@NotNull PsiType psiType) {
    return toBinary(psiType, true);
  }

  @NotNull
  private static String toBinary(@NotNull PsiType psiType, final boolean slashes) {
    return Optional.of(psiType)
                   .map(type -> TypeConversionUtil.erasure(type))
                   .map(type -> type.accept(createBinarySignatureVisitor(slashes)))
                   .orElseGet(() -> psiType.getPresentableText());
  }

  private static PsiTypeVisitor<String> createBinarySignatureVisitor(boolean slashes) {
    return new PsiTypeVisitor<String>() {
      @Override
      public String visitPrimitiveType(@NotNull PsiPrimitiveType primitiveType) {
        return primitiveType.getKind().getBinaryName();
      }

      @Override
      public String visitClassType(@NotNull PsiClassType classType) {
        PsiClass aClass = classType.resolve();
        if (aClass == null) {
          return "";
        }
        String jvmClassName = getJVMClassName(aClass);
        if (jvmClassName != null) {
          jvmClassName = "L" + (slashes ? jvmClassName.replace(".", "/") : jvmClassName) + ";";
        }
        return jvmClassName;
      }

      @Override
      public String visitArrayType(@NotNull PsiArrayType arrayType) {
        return "[" + arrayType.getComponentType().accept(this);
      }
    };
  }
}