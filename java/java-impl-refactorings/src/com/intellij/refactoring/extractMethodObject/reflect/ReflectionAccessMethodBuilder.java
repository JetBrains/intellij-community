// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractMethodObject.reflect;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.SmartList;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class ReflectionAccessMethodBuilder {
  private static final Logger LOG = Logger.getInstance(ReflectionAccessMethodBuilder.class);

  private boolean myIsStatic = false;
  private String myReturnType = "void";
  private final String myName;
  private MyBodyProvider myBodyProvider;
  private final List<ParameterInfo> myParameters = new SmartList<>();

  public ReflectionAccessMethodBuilder(@NotNull String name) {
    myName = name;
  }

  public PsiMethod build(@NotNull PsiElementFactory elementFactory,
                         @Nullable PsiElement context) {
    checkRequirements();
    String parameters = StreamEx.of(myParameters).map(p -> p.accessibleType + " " + p.name).joining(", ", "(", ")");
    String methodBody = myBodyProvider.createBody(myReturnType);
    List<String> possibleExceptions = myBodyProvider.getPossibleExceptions();
    if (!possibleExceptions.isEmpty()) {
      methodBody = "try {\n" +
                   methodBody +
                   "}\n" +
                   createCatchBlocks(possibleExceptions);
    }

    String methodText =
      "public" + (myIsStatic ? " static " : " ") + myReturnType + " " + myName + parameters + " { \n" + methodBody + "}\n";

    return elementFactory.createMethodFromText(methodText, context);
  }

  private void checkRequirements() {
    if (myBodyProvider == null) {
      LOG.error("Accessed member not specified");
    }
  }

  public ReflectionAccessMethodBuilder accessedMethod(@NotNull String jvmClassName, @NotNull String methodName) {
    myBodyProvider = new MyMethodAccessor(jvmClassName, methodName);
    return this;
  }

  public ReflectionAccessMethodBuilder accessedField(@NotNull String jvmClassName, @NotNull String fieldName) {
    myBodyProvider = new MyFieldAccessor(jvmClassName, fieldName, FieldAccessType.GET);
    return this;
  }

  public ReflectionAccessMethodBuilder updatedField(@NotNull String jvmClassName, @NotNull String fieldName) {
    myBodyProvider = new MyFieldAccessor(jvmClassName, fieldName, FieldAccessType.SET);
    return this;
  }

  public ReflectionAccessMethodBuilder accessedConstructor(@NotNull String jvmClassName) {
    myBodyProvider = new MyConstructorAccessor(jvmClassName);
    return this;
  }

  public ReflectionAccessMethodBuilder newArray(@NotNull String jvmClassName) {
    myBodyProvider = new MyNewArrayGenerator(jvmClassName);
    return this;
  }

  public ReflectionAccessMethodBuilder setReturnType(@NotNull String returnType) {
    myReturnType = returnType;
    return this;
  }

  public ReflectionAccessMethodBuilder setStatic(boolean isStatic) {
    myIsStatic = isStatic;
    return this;
  }

  public ReflectionAccessMethodBuilder addParameter(@NotNull String jvmType, @NotNull String name) {
    myParameters.add(new ParameterInfo(jvmType.replace('$', '.'), name, new TypeInfo(jvmType, 0)));
    return this;
  }

  public ReflectionAccessMethodBuilder addParameters(@NotNull PsiParameterList parameterList) {
    PsiParameter[] parameters = parameterList.getParameters();
    for (int i = 0; i < parameters.length; i++) {
      PsiType parameterType = parameters[i].getType();
      PsiType erasedType = TypeConversionUtil.erasure(parameterType);
      String typeName = typeName(parameterType, erasedType);
      TypeInfo jvmType = erasedType != null ? extractJvmType(erasedType) : new TypeInfo(typeName, 0);

      String name = "p" + i; // To avoid confusion with local variables, the real parameter names are not used.

      if (requiresObjectType(parameterType) || jvmType.arrayDimension > 0) {
        myParameters.add(new ParameterInfo(CommonClassNames.JAVA_LANG_OBJECT, name, jvmType));
      }
      else {
        PsiType accessedType = PsiReflectionAccessUtil.nearestAccessibleType(parameterType, parameterList);
        myParameters.add(new ParameterInfo(accessedType.getCanonicalText(), name, jvmType));
      }
    }

    return this;
  }

  private static boolean requiresObjectType(PsiType type) {
    PsiClass psiClass = PsiTypesUtil.getPsiClass(type);
    return psiClass != null && (psiClass.isRecord() || psiClass.isEnum());
  }

  private static @NotNull String typeName(@NotNull PsiType type, @Nullable PsiType erasedType) {
    if (erasedType == null) {
      String typeName = type.getCanonicalText();
      int typeParameterIndex = typeName.indexOf('<');
      if (typeParameterIndex != -1) {
        typeName = typeName.substring(0, typeParameterIndex);
      }

      LOG.warn("Type erasure failed, the following type used instead: " + typeName);
      return typeName;
    }

    return erasedType.getCanonicalText();
  }

  private static class TypeInfo {
    final int arrayDimension;
    final String typeName;

    private TypeInfo(String name, int dimension) {
      arrayDimension = dimension;
      typeName = name;
    }

    String lookupClass() {
      if (TypeConversionUtil.isPrimitive(typeName)) {
        return typeName + StringUtil.repeat("[]", arrayDimension) + ".class";
      }
      else {
        String className = typeName;
        if (arrayDimension > 0) {
          className = StringUtil.repeat("[", arrayDimension) + "L" + typeName + ";";
        }
        return "java.lang.Class.forName(\"" + className + "\")";
      }
    }
  }

  private static @NotNull TypeInfo extractJvmType(@NotNull PsiType type) {
    int arrayDimension = 0;
    while (type instanceof PsiArrayType arrayType) {
      arrayDimension++;
      type = arrayType.getComponentType();
    }
    PsiClass psiClass = PsiUtil.resolveClassInType(type);
    String canonicalText = type.getCanonicalText();
    String jvmName = psiClass == null ? canonicalText : ClassUtil.getJVMClassName(psiClass);
    return new TypeInfo(jvmName == null ? canonicalText : jvmName, arrayDimension);
  }

  private static String createCatchBlocks(@NotNull List<String> exceptions) {
    return StreamEx.of(exceptions).map(x -> "catch(" + x + " e) { throw new java.lang.RuntimeException(e); }").joining("\n");
  }

  private record ParameterInfo(@NotNull String accessibleType, @NotNull String name, @NotNull TypeInfo jvmType) {
  }

  private interface MyBodyProvider {
    List<String> getPossibleExceptions();
    String createBody(String returnType);
  }

  private static abstract class MyMemberAccessor implements MyBodyProvider {
    abstract String getMemberLookupExpression();
    abstract String getClassLookupExpression();
    abstract String getAccessExpression();
    abstract String getMemberType();

    @Override
    public String createBody(String returnType) {
      String returnExpression =
        ("void".equals(returnType) ? "member." : "return (" + returnType + ")member.") + getAccessExpression();
      return "  java.lang.Class<?> klass = " + getClassLookupExpression() + ";\n" +
             "  " + getMemberType() + " member = null;\n" +
             "  int interfaceNumber = -1;\n" +
             "  Class<?>[] interfaces = null;\n" +
             "  while (member == null) {\n" +
             "    try {\n" +
             "      member = klass." + getMemberLookupExpression() + ";\n" +
             "    } catch (java.lang.ReflectiveOperationException e) {\n" +
             "      if (interfaceNumber == -1) {\n" +
             "        interfaces = klass.getInterfaces();\n" +
             "        interfaceNumber = 0;\n" +
             "      }\n" +
             "      if (interfaceNumber < interfaces.length) {\n" +
             "        klass = interfaces[interfaceNumber];\n" +
             "        interfaceNumber += 1;\n" +
             "      } else {\n" +
             "        klass = klass.getSuperclass();\n" +
             "        if (klass == null) throw e;\n" +
             "        interfaceNumber = -1;\n" +
             "      }\n" +
             "    }\n" +
             "  }\n" +
             "  member.setAccessible(true);\n" +
             "  " + returnExpression + ";\n";
    }
  }

  private static class MyFieldAccessor extends MyMemberAccessor {
    private static final List<String> EXCEPTIONS = Collections.singletonList("java.lang.ReflectiveOperationException");
    private final String myFieldName;
    private final String myClassName;
    private final FieldAccessType myAccessType;

    MyFieldAccessor(@NotNull String className,
                           @NotNull String fieldName,
                           @NotNull FieldAccessType accessType) {
      myFieldName = fieldName;
      myClassName = className;
      myAccessType = accessType;
    }

    @Override
    public String getClassLookupExpression() {
      return PsiReflectionAccessUtil.classForName(myClassName);
    }

    @Override
    public String getMemberLookupExpression() {
      return "getDeclaredField(" + StringUtil.wrapWithDoubleQuote(myFieldName) + ")";
    }

    @Override
    public String getAccessExpression() {
      return FieldAccessType.GET.equals(myAccessType) ? "get(object)" : "set(object, value)";
    }

    @Override
    public String getMemberType() {
      return "java.lang.reflect.Field";
    }

    @Override
    public List<String> getPossibleExceptions() {
      return EXCEPTIONS;
    }
  }

  private class MyMethodAccessor extends MyMemberAccessor {
    private final String myClassName;
    private final String myMethodName;

    MyMethodAccessor(@NotNull String className, @NotNull String methodName) {
      myClassName = className;
      myMethodName = methodName;
    }

    @Override
    public String getMemberLookupExpression() {
      String args = StreamEx.of(myParameters).skip(1).map(x -> x.jvmType.lookupClass())
                            .prepend(StringUtil.wrapWithDoubleQuote(myMethodName))
                            .joining(", ", "(", ")");
      return "getDeclaredMethod" + args;
    }

    @Override
    public String getClassLookupExpression() {
      // emulate applySideEffectAndReturnNull().staticMethod() expression
      return PsiReflectionAccessUtil.classForName(myClassName);
    }

    @Override
    public String getMemberType() {
      return "java.lang.reflect.Method";
    }

    @Override
    public List<String> getPossibleExceptions() {
      return Collections.singletonList("java.lang.ReflectiveOperationException");
    }

    @Override
    public String getAccessExpression() {
      return "invoke" + parametersStringForInvoke();
    }
  }

  private class MyConstructorAccessor extends MyMemberAccessor {
    private final String myClassName;

    MyConstructorAccessor(@NotNull String className) {
      myClassName = className;
    }

    @Override
    public String getMemberLookupExpression() {
      String args = StreamEx.of(myParameters).map(x -> x.jvmType.lookupClass()).joining(", ", "(", ")");
      return "getDeclaredConstructor" + args;
    }

    @Override
    public String getClassLookupExpression() {
      return PsiReflectionAccessUtil.classForName(myClassName);
    }

    @Override
    public String getAccessExpression() {
      return "newInstance" + parametersStringForInvoke();
    }

    @Override
    public String getMemberType() {
      return "java.lang.reflect.Constructor<?>";
    }

    @Override
    public List<String> getPossibleExceptions() {
      return Collections.singletonList("java.lang.ReflectiveOperationException");
    }
  }

  private static class MyNewArrayGenerator implements MyBodyProvider {
    private final String myClassName;

    MyNewArrayGenerator(@NotNull String className) {
      myClassName = className;
    }

    @Override
    public String createBody(String returnType) {
      return "return (" + returnType + ")java.lang.reflect.Array.newInstance(" + PsiReflectionAccessUtil.classForName(myClassName) + ", dimensions);";
    }

    @Override
    public List<String> getPossibleExceptions() {
      return Collections.singletonList("java.lang.ReflectiveOperationException");
    }
  }

  private String parametersStringForInvoke() {
    return StreamEx.of(myParameters).map(x -> {
      if (x.jvmType.arrayDimension > 0) {
        return "(java.lang.Object)" + x.name; // cast arrays to Object to avoid confusion with varargs method invocation
      }
      else {
        return x.name;
      }
    }).joining(", ", "(", ")");
  }
}
