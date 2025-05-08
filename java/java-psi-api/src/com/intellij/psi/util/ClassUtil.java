// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.ide.util.JavaAnonymousClassesHelper;
import com.intellij.ide.util.JavaLocalClassesHelper;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ObjectIntHashMap;
import com.intellij.util.containers.ObjectIntMap;
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

  public static @NotNull @NlsSafe String extractClassName(@NotNull String fqName) {
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

  public static void formatClassName(final @NotNull PsiClass aClass, @NotNull StringBuilder buf) {
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

  private static int getNonQualifiedClassIdx(final @NotNull PsiClass psiClass, final @NotNull PsiClass containingClass) {
    ObjectIntMap<PsiClass> indices =
      CachedValuesManager.getCachedValue(containingClass, () -> {
        ObjectIntMap<PsiClass> map = new ObjectIntHashMap<>();
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

  public static PsiClass findNonQualifiedClassByIndex(@NotNull String indexName,
                                                      final @NotNull PsiClass containingClass,
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
      public void visitClass(@NotNull PsiClass aClass) {
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
      public void visitTypeParameter(final @NotNull PsiTypeParameter classParameter) {
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

  private static @NotNull String getDigitPrefix(@NotNull String indexName) {
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
  public static @Nullable PsiClass findPsiClass(@NotNull PsiManager manager, @NotNull String name) {
    return findPsiClass(manager, name, null, false);
  }

  public static @Nullable PsiClass findPsiClass(@NotNull PsiManager manager,
                                                @NotNull String name,
                                                @Nullable PsiClass parent,
                                                boolean jvmCompatible) {
    GlobalSearchScope scope = GlobalSearchScope.allScope(manager.getProject());
    return findPsiClass(manager, name, parent, jvmCompatible, scope);
  }

  public static @Nullable PsiClass findPsiClass(@NotNull PsiManager manager,
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

  private static @Nullable PsiClass findSubClass(@NotNull String name, @NotNull PsiClass parent, boolean jvmCompatible) {
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

  /**
   * Returns the binary class name for top-level and nested classes.
   *
   * @deprecated Does not work for anonymous classes and local classes. Use {@link #getBinaryClassName} instead.
   */
  @Deprecated
  public static @Nullable @NlsSafe String getJVMClassName(@NotNull PsiClass aClass) {
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
   * Returns the binary class name, i.e. the string that would be returned by {@link Class#getName}. See JLS 13.1.
   *
   * <ul>
   *   <li><b>Top-level classes</b> return the qualified name of the class, e.g. {@code com.example.Foo}</li>
   *   <li><b>Nested classes</b>, i.e. named classes nested directly within the body of another class, return the binary name of the outer
   *     class, followed by a {@code $} plus the name of the nested class, e.g. {@code com.example.Foo$Inner}</li>
   *   <li><b>Anonymous classes</b>, i.e. unnamed classes created with a {@code new} expression, return the binary name of the class they
   *     are within, followed by a {@code $} plus a number indexing which anonymous class within the outer class we are referring to, e.g.
   *     {@code com.example.Foo$1}</li>
   *   <li><b>Local classes</b>, i.e. named classes within a method, return the binary name of the class they are within, followed by a
   *     {@code $} plus a number and then the local class' name. The number indexes which local class with that same name within the outer
   *     class we are referring to (multiple local classes within the same class may have the same name if they are in different methods).
   *     E.g. {@code com.example.Foo$1Local}</li>
   * </ul>
   */
  public static @Nullable @NlsSafe String getBinaryClassName(@NotNull PsiClass aClass) {
    if (PsiUtil.isLocalOrAnonymousClass(aClass)) {
      PsiClass parentClass = PsiTreeUtil.getParentOfType(aClass, PsiClass.class);
      if (parentClass == null) {
        return null;
      }
      String parentName = getBinaryClassName(parentClass);
      if (parentName == null) {
        return null;
      }
      if (aClass instanceof PsiAnonymousClass) {
        return parentName + JavaAnonymousClassesHelper.getName((PsiAnonymousClass) aClass);
      } else {
        return parentName + JavaLocalClassesHelper.getName(aClass);
      }
    }

    return getJVMClassName(aClass);
  }

  /**
   * Looks for inner and anonymous classes by internal name ('pkg/Top$Inner').
   */
  public static @Nullable PsiClass findPsiClassByJVMName(@NotNull PsiManager manager, @NotNull String jvmClassName) {
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
    signature.append(getBinaryPresentation(Optional.ofNullable(method.getReturnType()).orElse(PsiTypes.voidType())));
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

  public static @NotNull String getClassObjectPresentation(@NotNull PsiType psiType) {
     return toBinary(psiType, false);
  }

  public static @NotNull String getBinaryPresentation(@NotNull PsiType psiType) {
    return toBinary(psiType, true);
  }

  private static @NotNull String toBinary(@NotNull PsiType psiType, final boolean slashes) {
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