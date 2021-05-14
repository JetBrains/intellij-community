// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.bytecodeAnalysis;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.org.objectweb.asm.Handle;
import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.jetbrains.org.objectweb.asm.tree.analysis.BasicValue;

import java.util.List;

import static org.jetbrains.org.objectweb.asm.Opcodes.*;

final class LambdaIndy {
  private static final String LAMBDA_METAFACTORY_CLASS = "java/lang/invoke/LambdaMetafactory";
  private static final String LAMBDA_METAFACTORY_METHOD = "metafactory";
  private static final String LAMBDA_METAFACTORY_DESCRIPTOR =
    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;";

  private final int myTag;
  private final Type myFunctionalMethodType;
  private final Member myMethod;
  private final Type myFunctionalInterfaceType;

  private LambdaIndy(int tag, Type functionalMethodType, Member lambdaMethod, Type functionalInterfaceType) {
    myTag = tag;
    myFunctionalMethodType = functionalMethodType;
    myMethod = lambdaMethod;
    myFunctionalInterfaceType = functionalInterfaceType;
  }

  public int getTag() {
    return myTag;
  }

  /**
   * @return an opcode which corresponds to target method handle or -1 if method handle tag has no corresponding opcode
   */
  public int getAssociatedOpcode() {
    switch (myTag) {
      case H_INVOKESTATIC:
        return INVOKESTATIC;
      case H_INVOKESPECIAL:
        return INVOKESPECIAL;
      case H_INVOKEINTERFACE:
        return INVOKEINTERFACE;
      case H_INVOKEVIRTUAL:
        return INVOKEVIRTUAL;
    }
    return -1;
  }

  public Type getFunctionalMethodType() {
    return myFunctionalMethodType;
  }

  public Member getMethod() {
    return myMethod;
  }

  public Type getFunctionalInterfaceType() {
    return myFunctionalInterfaceType;
  }

  /**
   * Creates list of argument values which should be passed to lambda runtime representation method
   *
   * @param captured list of captured arguments
   * @param valueSupplier function to create new values by type
   * @return list of lambda argument values
   */
  List<BasicValue> getLambdaMethodArguments(List<? extends BasicValue> captured, Function<? super Type, ? extends BasicValue> valueSupplier) {
    // Lambda runtime representation args consist of captured values and invocation values
    // E.g.:
    // IntUnaryOperator getAdder(int addend) { return x -> addend + x; }
    // will generate
    // static int lambda$getAdder$0(int addend, int x) {return addend + x;}
    return ContainerUtil.concat(captured, ContainerUtil.map(getFunctionalMethodType().getArgumentTypes(), valueSupplier));
  }

  public String toString() {
    return "Lambda [" + myMethod.methodName + "]: " + StringUtil.getShortName(myFunctionalInterfaceType.getClassName());
  }

  static LambdaIndy from(InvokeDynamicInsnNode indyNode) {
    Handle bsm = indyNode.bsm;
    if (LAMBDA_METAFACTORY_CLASS.equals(bsm.getOwner()) &&
       LAMBDA_METAFACTORY_METHOD.equals(bsm.getName()) &&
       LAMBDA_METAFACTORY_DESCRIPTOR.equals(bsm.getDesc()) &&
       indyNode.bsmArgs.length >= 3 && indyNode.bsmArgs[1] instanceof Handle && indyNode.bsmArgs[2] instanceof Type) {
      Handle lambdaBody = (Handle)indyNode.bsmArgs[1];
      Type targetType = (Type)indyNode.bsmArgs[2];
      Member targetMethod = new Member(lambdaBody.getOwner(), lambdaBody.getName(), lambdaBody.getDesc());
      Type retType = Type.getReturnType(indyNode.desc);
      return new LambdaIndy(lambdaBody.getTag(), targetType, targetMethod, retType);
    }
    return null;
  }
}