package com.intellij.compilerOutputIndex.api.fs;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.asm4.Opcodes;
import org.jetbrains.asm4.Type;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public final class AsmUtil implements Opcodes {

  private AsmUtil() {}

  public static boolean isStaticMethodDeclaration(final int access) {
    return (access & Opcodes.ACC_STATIC) != 0;
  }

  public static boolean isStaticMethodInvocation(final int opcode) {
    return opcode == Opcodes.INVOKESTATIC;
  }

  public static String getQualifiedClassName(final String name) {
    return asJavaInnerClassQName(Type.getObjectType(name).getClassName());
  }

  public static String getReturnType(final String desc) {
    return asJavaInnerClassQName(Type.getReturnType(desc).getClassName());
  }

  public static String[] getQualifiedClassNames(final String[] classNames, final String... yetAnotherClassNames) {
    final List<String> qualifiedClassNames = new ArrayList<String>(classNames.length + yetAnotherClassNames.length);
    for (final String className : classNames) {
      qualifiedClassNames.add(getQualifiedClassName(className));
    }
    for (final String className : yetAnotherClassNames) {
      if (className != null) {
        qualifiedClassNames.add(getQualifiedClassName(className));
      }
    }
    return ArrayUtil.toStringArray(qualifiedClassNames);
  }

  public static String[] getParamsTypes(final String desc) {
    final Type[] types = Type.getArgumentTypes(desc);
    final String[] typesAsString = new String[types.length];
    for (int i = 0; i < types.length; i++) {
      typesAsString[i] = types[i].getClassName();
    }
    return typesAsString;
  }

  @Nullable
  public static String getSignature(final PsiMethod psiMethod) {
    final PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
    final StringBuilder sb = new StringBuilder();
    sb.append("(");
    for (final PsiParameter p : parameters) {
      final String desc = getDescriptor(p);
      if (desc == null) {
        return null;
      }
      sb.append(desc);
    }
    sb.append(")");
    final String desc = getDescriptor(psiMethod.getReturnType());
    if (desc == null) {
      return null;
    }
    sb.append(desc);
    return sb.toString();
  }

  @Nullable
  private static String getDescriptor(final PsiParameter parameter) {
    return getDescriptor(parameter.getType());
  }

  @Nullable
  private static String getDescriptor(@Nullable final PsiType type) {
    if (type == null) {
      return null;
    }
    if (type instanceof PsiPrimitiveType) {
      final PsiPrimitiveType primitiveType = (PsiPrimitiveType) type;
      if (PsiType.INT.equals(primitiveType)) {
        return "I";
      } else if (primitiveType.equals(PsiType.VOID)) {
        return "V";
      } else if (primitiveType.equals(PsiType.BOOLEAN)) {
        return "Z";
      } else if (primitiveType.equals(PsiType.BYTE)) {
        return "B";
      } else if (primitiveType.equals(PsiType.CHAR)) {
        return "C";
      } else if (primitiveType.equals(PsiType.SHORT)) {
        return "S";
      } else if (primitiveType.equals(PsiType.DOUBLE)) {
        return "D";
      } else if (primitiveType.equals(PsiType.FLOAT)) {
        return "F";
      } else /* if (primitiveType.equals(PsiType.LONG)) */ {
        return "J";
      }
    } else if (type instanceof PsiArrayType) {
      return "[" + getDescriptor(((PsiArrayType) type).getComponentType());
    } else {
      final PsiClassType classType = (PsiClassType) type;
      final PsiClass aClass = classType.resolve();
      if (aClass == null) {
        return null;
      }
      final String qName = aClass.getQualifiedName();
      if (qName == null) {
        return null;
      }
      return "L" + StringUtil.replace(qName, ".", "/") + ";";
    }
  }

  private static String asJavaInnerClassQName(final String byteCodeClassQName) {
    return StringUtil.replaceChar(byteCodeClassQName, '$', '.');
  }
}
