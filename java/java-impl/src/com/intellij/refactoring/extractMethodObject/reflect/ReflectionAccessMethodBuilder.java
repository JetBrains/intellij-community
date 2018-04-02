// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethodObject.reflect;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.ClassUtil;
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
  private MyMemberAccessor myMemberAccessor;
  private final List<ParameterInfo> myParameters = new SmartList<>();

  public ReflectionAccessMethodBuilder(@NotNull String name) {
    myName = name;
  }

  public PsiMethod build(@NotNull PsiElementFactory elementFactory,
                         @Nullable PsiElement context) {
    checkRequirements();
    String parameters = StreamEx.of(myParameters).map(p -> p.type + " " + p.name).joining(", ", "(", ")");
    String returnExpression =
      ("void".equals(myReturnType) ? "member." : "return (" + myReturnType + ")member.") + myMemberAccessor.getAccessExpression();
    String methodBody = "  java.lang.Class<?> klass = " + myMemberAccessor.getClassLookupExpression() + ";\n" +
                        "  " + myMemberAccessor.getMemberType() + " member = null;\n" +
                        "  int interfaceNumber = -1;\n" +
                        "  Class<?>[] interfaces = null;\n" +
                        "  while (member == null) {\n" +
                        "    try {\n" +
                        "      member = klass." + myMemberAccessor.getMemberLookupExpression() + ";\n" +
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
    List<String> possibleExceptions = myMemberAccessor.getPossibleExceptions();
    if (!possibleExceptions.isEmpty()) {
      methodBody = "try {\n" +
                   methodBody +
                   "}" +
                   createCatchBlocks(possibleExceptions);
    }

    String methodText =
      "public" + (myIsStatic ? " static " : " ") + myReturnType + " " + myName + parameters + " { \n" + methodBody + "}\n";

    return elementFactory.createMethodFromText(methodText, context);
  }

  private void checkRequirements() {
    if (myMemberAccessor == null) {
      LOG.error("Accessed member not specified");
    }
  }

  public ReflectionAccessMethodBuilder accessedMethod(@NotNull String jvmClassName, @NotNull String methodName) {
    myMemberAccessor = new MyMethodAccessor(jvmClassName, methodName);
    return this;
  }

  public ReflectionAccessMethodBuilder accessedField(@NotNull String jvmClassName, @NotNull String fieldName) {
    myMemberAccessor = new MyFieldAccessor(jvmClassName, fieldName, FieldAccessType.GET);
    return this;
  }

  public ReflectionAccessMethodBuilder updatedField(@NotNull String jvmClassName, @NotNull String fieldName) {
    myMemberAccessor = new MyFieldAccessor(jvmClassName, fieldName, FieldAccessType.SET);
    return this;
  }

  public ReflectionAccessMethodBuilder accessedConstructor(@NotNull String jvmClassName) {
    myMemberAccessor = new MyConstructorAccessor(jvmClassName);
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
    myParameters.add(new ParameterInfo(jvmType.replace('$', '.'), name, jvmType));
    return this;
  }

  public ReflectionAccessMethodBuilder addParameters(@NotNull PsiParameterList parameterList) {
    PsiParameter[] parameters = parameterList.getParameters();
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      String name = parameter.getName();
      PsiType parameterType = parameter.getType();
      PsiType erasedType = TypeConversionUtil.erasure(parameterType);
      String typeName = typeName(parameterType, erasedType);
      String jvmType = erasedType != null ? extractJvmType(erasedType) : typeName;

      if (name == null) {
        LOG.warn("Parameter name not found, index = " + i + ", type = " + typeName);
        name = "arg" + i;
      }

      myParameters.add(new ParameterInfo(typeName, name, jvmType));
    }

    return this;
  }

  @NotNull
  private static String typeName(@NotNull PsiType type, @Nullable PsiType erasedType) {
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

  @NotNull
  private static String extractJvmType(@NotNull PsiType type) {
    PsiClass psiClass = PsiUtil.resolveClassInType(type);
    String canonicalText = type.getCanonicalText();
    String jvmName = psiClass == null ? canonicalText : ClassUtil.getJVMClassName(psiClass);
    return jvmName == null ? canonicalText : jvmName;
  }

  private static String createCatchBlocks(@NotNull List<String> exceptions) {
    return StreamEx.of(exceptions).map(x -> "catch(" + x + " e) { throw new java.lang.RuntimeException(e); }").joining("\n");
  }

  private static class ParameterInfo {
    public final String type;
    public final String name;
    public final String jvmTypeName;

    public ParameterInfo(@NotNull String type, @NotNull String name) {
      this(type, name, type);
    }

    public ParameterInfo(@NotNull String type, @NotNull String name, @NotNull String jvmTypeName) {
      this.type = type;
      this.name = name;
      this.jvmTypeName = jvmTypeName;
    }
  }

  private interface MyMemberAccessor {
    String getMemberLookupExpression();

    String getClassLookupExpression();

    String getAccessExpression();

    String getMemberType();

    List<String> getPossibleExceptions();
  }


  private static class MyFieldAccessor implements MyMemberAccessor {
    private static final List<String> EXCEPTIONS = Collections.unmodifiableList(
      Collections.singletonList("java.lang.ReflectiveOperationException"));
    private final String myFieldName;
    private final String myClassName;
    private final FieldAccessType myAccessType;

    public MyFieldAccessor(@NotNull String className,
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


  private class MyMethodAccessor implements MyMemberAccessor {
    private final String myClassName;
    private final String myMethodName;

    public MyMethodAccessor(@NotNull String className, @NotNull String methodName) {
      myClassName = className;
      myMethodName = methodName;
    }

    @Override
    public String getMemberLookupExpression() {
      String args = StreamEx.of(myParameters).skip(1).map(x -> PsiReflectionAccessUtil.classForName(x.jvmTypeName))
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
      return Collections.unmodifiableList(Collections.singletonList("java.lang.ReflectiveOperationException"));
    }

    @Override
    public String getAccessExpression() {
      return StreamEx.of(myParameters).map(x -> x.name).joining(", ", "invoke(", ")");
    }
  }


  private class MyConstructorAccessor implements MyMemberAccessor {
    private final String myClassName;

    public MyConstructorAccessor(@NotNull String className) {
      myClassName = className;
    }

    @Override
    public String getMemberLookupExpression() {
      String args = StreamEx.of(myParameters).map(x -> x.jvmTypeName).map(PsiReflectionAccessUtil::classForName).joining(", ", "(", ")");
      return "getDeclaredConstructor" + args;
    }

    @Override
    public String getClassLookupExpression() {
      return PsiReflectionAccessUtil.classForName(myClassName);
    }

    @Override
    public String getAccessExpression() {
      String args = StreamEx.of(myParameters).map(x -> x.name).joining(", ", "(", ")");
      return "newInstance" + args;
    }

    @Override
    public String getMemberType() {
      return "java.lang.reflect.Constructor<?>";
    }

    @Override
    public List<String> getPossibleExceptions() {
      return Collections.unmodifiableList(Collections.singletonList("java.lang.ReflectiveOperationException"
      ));
    }
  }
}
