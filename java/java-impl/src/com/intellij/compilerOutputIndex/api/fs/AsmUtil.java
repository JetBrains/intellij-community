package com.intellij.compilerOutputIndex.api.fs;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.asm4.Opcodes;
import org.jetbrains.asm4.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public final class AsmUtil implements Opcodes {

  private AsmUtil() {}

  public static boolean isStaticMethodDeclaration(final int access) {
    return (access & Opcodes.ACC_STATIC) != 0;
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

  private static String asJavaInnerClassQName(final String byteCodeClassQName) {
    return StringUtil.replaceChar(byteCodeClassQName, '$', '.');
  }

  //char
  //double
  //float
  //int
  //long
  //short
  //boolean
  //byte
  //void
  //Object
  //String
  //Class
  private static final Set<String> ASM_PRIMITIVE_TYPES = ContainerUtil.newHashSet("C", "D", "F", "I", "J", "S", "Z", "B", "V",
                                                                                  "Ljava/lang/Object;",
                                                                                  "Ljava/lang/String;",
                                                                                  "Ljava/lang/Class;");

  public static boolean isPrimitive(final String asmType) {
    return ASM_PRIMITIVE_TYPES.contains(asmType);
  }

  public static boolean isPrimitiveOrArray(final String asmType) {
    if (asmType.startsWith("[")) {
      return true;
    }
    return isPrimitive(asmType);
  }
}
