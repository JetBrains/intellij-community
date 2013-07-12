package com.intellij.compilerOutputIndex.impl.callingLocation;

import com.intellij.codeInsight.completion.methodChains.ChainCompletionStringUtil;
import com.intellij.compilerOutputIndex.api.fs.AsmUtil;
import com.intellij.compilerOutputIndex.impl.MethodIncompleteSignature;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.asm4.*;
import org.jetbrains.asm4.commons.AnalyzerAdapter;
import org.jetbrains.asm4.commons.JSRInlinerAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public class MethodCallingLocationExtractor {
  private MethodCallingLocationExtractor() {
  }

  public static Map<MethodNameAndQualifier, List<CallingLocation>> extract(final ClassReader classReader) {
    final MyClassVisitor classVisitor = new MyClassVisitor();
    classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES);
    return classVisitor.getExtractedMethodsCallings();
  }

  private static class MyClassVisitor extends ClassVisitor {
    public MyClassVisitor() {
      super(Opcodes.ASM4);
    }

    private final Map<MethodNameAndQualifier, List<CallingLocation>> myExtractedMethodsCallings =
      new HashMap<MethodNameAndQualifier, List<CallingLocation>>();

    private String myClassName;
    private String myRawClassName;

    private Map<MethodNameAndQualifier, List<CallingLocation>> getExtractedMethodsCallings() {
      return myExtractedMethodsCallings;
    }

    @Override
    public void visit(final int version,
                      final int access,
                      final String className,
                      final String signature,
                      final String superName,
                      final String[] interfaces) {
      myRawClassName = className;
      myClassName = AsmUtil.getQualifiedClassName(className);
    }

    @Nullable
    @Override
    public FieldVisitor visitField(final int access, final String name, final String desc, final String signature, final Object value) {
      return null;
    }

    @Nullable
    @Override
    public MethodVisitor visitMethod(final int access,
                                     final String name,
                                     final String desc,
                                     final String signature,
                                     final String[] exceptions) {

      if (name.charAt(0) == '<') {
        return null;
      }
      final boolean isStaticMethod = AsmUtil.isStaticMethodDeclaration(access);
      if (isStaticMethod) {
        return null;
      }
      @SuppressWarnings("UnnecessaryLocalVariable") final String methodName = name;
      final String[] methodParams = AsmUtil.getParamsTypes(desc);
      final MethodIncompleteSignature currentMethodSignature =
        new MethodIncompleteSignature(myClassName, AsmUtil.getReturnType(desc), methodName, isStaticMethod);
      return new JSRInlinerAdapter(new AnalyzerAdapter(Opcodes.ASM4, myRawClassName, access, name, desc, null) {
        private final Map<Integer, Variable> myFieldsAndParamsPositionInStack = new HashMap<Integer, Variable>();

        @Override
        public void visitInsn(final int opcode) {
          super.visitInsn(opcode);
        }

        @Override
        public void visitFieldInsn(final int opcode, final String owner, final String name, final String desc) {
          boolean onThis = false;
          if (stack != null && opcode == Opcodes.GETFIELD && !ChainCompletionStringUtil.isPrimitiveOrArray(AsmUtil.getReturnType(desc))) {
            final Object objectRef = stack.get(stack.size() - 1);
            if (objectRef instanceof String && objectRef.equals(myRawClassName)) {
              onThis = true;
            }
          }
          super.visitFieldInsn(opcode, owner, name, desc);
          if (onThis) {
            final int index = stack.size() - 1;
            final Object marker = stack.get(index);
            myFieldsAndParamsPositionInStack.put(index, new Variable(marker, VariableType.FIELD));
          }
        }

        @Override
        public void visitVarInsn(final int opcode, final int varIndex) {
          super.visitVarInsn(opcode, varIndex);
          if (stack != null && opcode == Opcodes.ALOAD &&
              varIndex > 0 &&
              varIndex <= methodParams.length &&
              !ChainCompletionStringUtil.isPrimitiveOrArray(methodParams[varIndex - 1])) {
            final int stackPos = stack.size() - 1;
            myFieldsAndParamsPositionInStack.put(stackPos, new Variable(stack.get(stackPos), VariableType.METHOD_PARAMETER));
          }
        }

        @Override
        public void visitMethodInsn(final int opcode, final String owner, final String name, final String desc) {
          if (stack != null && opcode != Opcodes.INVOKESTATIC && !methodName.startsWith("<")) {
            final int index = stack.size() - 1 - AsmUtil.getParamsTypes(desc).length;
            final Object stackValue = stack.get(index);
            final Variable variable = myFieldsAndParamsPositionInStack.get(index);
            if (variable != null && variable.getMarker() == stackValue /*equality by reference is not mistake*/) {
              final CallingLocation callingLocation = new CallingLocation(currentMethodSignature, variable.getVariableType());
              final MethodNameAndQualifier invokedMethod = new MethodNameAndQualifier(name, AsmUtil.getQualifiedClassName(owner));
              List<CallingLocation> callingLocations = myExtractedMethodsCallings.get(invokedMethod);
              if (callingLocations == null) {
                callingLocations = new ArrayList<CallingLocation>();
                myExtractedMethodsCallings.put(invokedMethod, callingLocations);
              }
              callingLocations.add(callingLocation);
            }
          }
          super.visitMethodInsn(opcode, owner, name, desc);
        }
      }, access, name, desc, signature, exceptions);
    }
  }

  private static class Variable {
    private final Object myMarker;
    private final VariableType myVariableType;

    private Variable(final Object marker, final VariableType variableType) {
      myMarker = marker;
      myVariableType = variableType;
    }

    private Object getMarker() {
      return myMarker;
    }

    private VariableType getVariableType() {
      return myVariableType;
    }
  }
}
