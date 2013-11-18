package com.intellij.compilerOutputIndex.impl;

import com.intellij.compilerOutputIndex.api.fs.AsmUtil;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public class MethodIncompleteSignature {

  public static final String CONSTRUCTOR_METHOD_NAME = "<init>";

  @NotNull
  private final String myOwner;
  @NotNull
  private final String myReturnType;
  @NotNull
  private final String myName;
  private final boolean myStatic;

  public MethodIncompleteSignature(@NotNull final String owner,
                                   @NotNull final String returnType,
                                   @NotNull final String name,
                                   final boolean aStatic) {
    myOwner = owner;
    myReturnType = returnType;
    myName = name;
    myStatic = aStatic;
  }

  public static MethodIncompleteSignature constructor(@NotNull final String className) {
    return new MethodIncompleteSignature(className, className, CONSTRUCTOR_METHOD_NAME, true);
  }

  public MethodIncompleteSignature toExternalRepresentation() {
    return new MethodIncompleteSignature(AsmUtil.getQualifiedClassName(getOwner()),
                                         AsmUtil.getQualifiedClassName(getReturnType()),
                                         getName(),
                                         isStatic());
  }

  @NotNull
  public String getOwner() {
    return myOwner;
  }

  @NotNull
  public String getReturnType() {
    return myReturnType;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  public boolean isStatic() {
    return myStatic;
  }

  @NotNull
  public PsiMethod[] resolveNotDeprecated(final JavaPsiFacade javaPsiFacade, final GlobalSearchScope scope) {
    return notDeprecated(resolve(javaPsiFacade, scope));
  }

  @NotNull
  public PsiMethod[] resolve(final JavaPsiFacade javaPsiFacade, final GlobalSearchScope scope) {
    if (CONSTRUCTOR_METHOD_NAME.equals(getName())) {
      return PsiMethod.EMPTY_ARRAY;
    }
    final PsiClass aClass = javaPsiFacade.findClass(getOwner(), scope);
    if (aClass == null) {
      return PsiMethod.EMPTY_ARRAY;
    }
    final PsiMethod[] methods = aClass.findMethodsByName(getName(), true);
    final List<PsiMethod> filtered = new ArrayList<PsiMethod>(methods.length);
    for (final PsiMethod method : methods) {
      if (method.hasModifierProperty(PsiModifier.STATIC) == isStatic()) {
        final PsiType returnType = method.getReturnType();
        if (returnType != null && returnType.equalsToText(getReturnType())) {
          filtered.add(method);
        }
      }
    }
    if (filtered.size() > 1) {
      Collections.sort(filtered, new Comparator<PsiMethod>() {
        @Override
        public int compare(final PsiMethod o1, final PsiMethod o2) {
          return o1.getParameterList().getParametersCount() - o2.getParameterList().getParametersCount();
        }
      });
    }
    return filtered.toArray(new PsiMethod[filtered.size()]);
  }

  public static KeyDescriptor<MethodIncompleteSignature> createKeyDescriptor() {
    final EnumeratorStringDescriptor stringDescriptor = new EnumeratorStringDescriptor();
    return new KeyDescriptor<MethodIncompleteSignature>() {
      @Override
      public void save(final DataOutput out, final MethodIncompleteSignature value) throws IOException {
        stringDescriptor.save(out, value.getOwner());
        stringDescriptor.save(out, value.getReturnType());
        stringDescriptor.save(out, value.getName());
        out.writeBoolean(value.isStatic());
      }

      @Override
      public MethodIncompleteSignature read(final DataInput in) throws IOException {
        return new MethodIncompleteSignature(stringDescriptor.read(in), stringDescriptor.read(in), stringDescriptor.read(in),
                                             in.readBoolean());
      }

      @Override
      public int getHashCode(final MethodIncompleteSignature value) {
        return value.hashCode();
      }

      @Override
      public boolean isEqual(final MethodIncompleteSignature val1, final MethodIncompleteSignature val2) {
        return val1.equals(val2);
      }
    };
  }

  @NotNull
  private static PsiMethod[] notDeprecated(@NotNull final PsiMethod[] methods) {
    final List<PsiMethod> filtered = ContainerUtil.filter(methods, NOT_DEPRECATED_CONDITION);
    return filtered.toArray(new PsiMethod[filtered.size()]);
  }

  private final static Condition<PsiMethod> NOT_DEPRECATED_CONDITION = new Condition<PsiMethod>() {
    @Override
    public boolean value(final PsiMethod method) {
      return !method.isDeprecated();
    }
  };

  public final static Comparator<MethodIncompleteSignature> COMPARATOR = new Comparator<MethodIncompleteSignature>() {
    @Override
    public int compare(final MethodIncompleteSignature o1, final MethodIncompleteSignature o2) {
      int sub = o1.getOwner().compareTo(o2.getOwner());
      if (sub != 0) {
        return sub;
      }
      sub = o1.getName().compareTo(o2.getName());
      if (sub != 0) {
        return sub;
      }
      sub = o1.getReturnType().compareTo(o2.getReturnType());
      if (sub != 0) {
        return sub;
      }
      if (o1.isStatic() && !o2.isStatic()) {
        return 1;
      }
      if (o2.isStatic() && !o1.isStatic()) {
        return -1;
      }
      return 0;
    }
  };

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final MethodIncompleteSignature that = (MethodIncompleteSignature)o;

    if (myStatic != that.myStatic) return false;
    if (!myName.equals(that.myName)) return false;
    if (!myOwner.equals(that.myOwner)) return false;
    if (!myReturnType.equals(that.myReturnType)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myOwner.hashCode();
    result = 31 * result + myReturnType.hashCode();
    result = 31 * result + myName.hashCode();
    result = 31 * result + (myStatic ? 1 : 0);
    return result;
  }
}
