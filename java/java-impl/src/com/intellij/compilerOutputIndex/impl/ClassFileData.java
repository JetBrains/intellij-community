package com.intellij.compilerOutputIndex.impl;

import com.intellij.codeInsight.completion.methodChains.ChainCompletionStringUtil;
import com.intellij.compilerOutputIndex.api.fs.AsmUtil;
import org.jetbrains.asm4.ClassReader;
import org.jetbrains.asm4.ClassVisitor;
import org.jetbrains.asm4.MethodVisitor;
import org.jetbrains.asm4.Opcodes;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public class ClassFileData {
  private final List<MethodData> myMethodDatas;

  public ClassFileData(final ClassReader classReader) {
    this(classReader, true);
  }

  public ClassFileData(final ClassReader classReader, final boolean checkForPrimitiveReturn) {
    myMethodDatas = new ArrayList<MethodData>();
    classReader.accept(new ClassVisitor(Opcodes.ASM4) {
      @Override
      public MethodVisitor visitMethod(final int access,
                                       final String name,
                                       final String desc,
                                       final String signature,
                                       final String[] exceptions) {
        final MethodDataAccumulator methodDataAccumulator = new MethodDataAccumulator(checkForPrimitiveReturn);
        myMethodDatas.add(methodDataAccumulator.getMethodData());
        return methodDataAccumulator;
      }
    }, Opcodes.ASM4);
  }

  public List<MethodData> getMethodDatas() {
    return myMethodDatas;
  }

  public static class MethodData {
    private final List<MethodInsnSignature> myMethodInsnSignatures = new ArrayList<MethodInsnSignature>();

    private void addSign(final MethodInsnSignature signature) {
      myMethodInsnSignatures.add(signature);
    }

    public List<MethodInsnSignature> getMethodInsnSignatures() {
      return myMethodInsnSignatures;
    }
  }

  private static class MethodDataAccumulator extends MethodVisitor {
    private final MethodData myMethodData = new MethodData();
    private final boolean myCheckForPrimitiveReturn;

    public MethodDataAccumulator(final boolean checkForPrimitiveReturn) {
      super(Opcodes.ASM4);
      myCheckForPrimitiveReturn = checkForPrimitiveReturn;
    }

    private MethodData getMethodData() {
      return myMethodData;
    }

    @Override
    public void visitMethodInsn(final int opcode, final String owner, final String name, final String desc) {
      if (MethodIncompleteSignature.CONSTRUCTOR_METHOD_NAME.equals(name)) {
        return;
      }
      final String ownerClassName = AsmUtil.getQualifiedClassName(owner);
      if (ChainCompletionStringUtil.isPrimitiveOrArrayOfPrimitives(ownerClassName)) {
        return;
      }
      if (myCheckForPrimitiveReturn) {
        final String returnType = AsmUtil.getReturnType(desc);
        if (ChainCompletionStringUtil.isPrimitiveOrArrayOfPrimitives(returnType)) {
          return;
        }
      }
      myMethodData.addSign(new MethodInsnSignature(opcode, owner, name, desc));
    }
  }

  public static class MethodInsnSignature {
    private final int myOpcode;
    private final String myOwner;
    private final String myName;
    private final String myDesc;

    private MethodInsnSignature(final int opcode, final String owner, final String name, final String desc) {
      myOpcode = opcode;
      myOwner = owner;
      myName = name;
      myDesc = desc;
    }

    public int getOpcode() {
      return myOpcode;
    }

    public String getOwner() {
      return myOwner;
    }

    public String getName() {
      return myName;
    }

    public String getDesc() {
      return myDesc;
    }
  }
}
