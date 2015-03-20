/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.debugger.jdi;

import org.jetbrains.annotations.Nullable;

/**
 * "Code" attribute parsing extracted from ASM4 library and adopted to finding operations with local variables
 */
public class InstructionParser {
  private final byte[] myCode;
  private final long myStopOffset;

  public InstructionParser(byte[] code, long stopOffset) {
    myCode = code;
    myStopOffset = stopOffset;
  }

  public void parse()  {
    int v = 0;
    while (v < myStopOffset) {
      int opcode = myCode[v] & 0xFF;
      final byte opcodeType = opcode == Bytecodes.IMPDEP1 || opcode == Bytecodes.IMPDEP2? Bytecodes.NOARG_INSN : Bytecodes.TYPE[opcode];
      switch (opcodeType) {
        case Bytecodes.NOARG_INSN:
          v += 1;
          break;
        case Bytecodes.IMPLVAR_INSN: {
          final int varOpcode;
          if (opcode > Bytecodes.ISTORE) {
            opcode -= Bytecodes.ISTORE_0;
            varOpcode = Bytecodes.ISTORE + (opcode >> 2);
          }
          else {
            opcode -= Bytecodes.ILOAD_0;
            varOpcode = Bytecodes.ILOAD + (opcode >> 2);
          }
          final String signature = getVarInstructionTypeSignature(varOpcode);
          if (signature != null) {
            localVariableInstructionFound(varOpcode, opcode & 0x3, signature);
          }
          v += 1;
          break;
        }
        case Bytecodes.LABEL_INSN:
          v += 3;
          break;
        case Bytecodes.LABELW_INSN:
          v += 5;
          break;
        case Bytecodes.WIDE_INSN: {
          opcode = myCode[v + 1] & 0xFF;
          final String signature = getVarInstructionTypeSignature(opcode);
          if (signature != null) {
            localVariableInstructionFound(opcode, readUnsignedShort(v + 2), signature);
          }
          if (opcode == Bytecodes.IINC) {
            v += 6;
          }
          else {
            v += 4;
          }
          break;
        }
        case Bytecodes.TABL_INSN:
          // skips 0 to 3 padding bytes
          v = v + 4 - (v & 3);
          // reads instruction
          int min = readInt(v + 4);
          int max = readInt(v + 8);
          v += 12;
          final int length = max - min + 1;
          v += 4 * length;
          break;
        case Bytecodes.LOOK_INSN:
          // skips 0 to 3 padding bytes
          v = v + 4 - (v & 3);
          // reads instruction
          final int len = readInt(v + 4);
          v += 8 * (len + 1);
          break;
        case Bytecodes.VAR_INSN: {
          final String signature = getVarInstructionTypeSignature(opcode);
          if (signature != null) {
            localVariableInstructionFound(opcode, myCode[v + 1] & 0xFF, signature);
          }
          v += 2;
          break;
        }
        case Bytecodes.SBYTE_INSN:
          v += 2;
          break;
        case Bytecodes.SHORT_INSN:
          v += 3;
          break;
        case Bytecodes.LDC_INSN:
          v += 2;
          break;
        case Bytecodes.LDCW_INSN:
          v += 3;
          break;
        case Bytecodes.FIELDORMETH_INSN:
        case Bytecodes.ITFMETH_INSN: {
          if (opcode == Bytecodes.INVOKEINTERFACE) {
            v += 5;
          }
          else {
            v += 3;
          }
          break;
        }
        case Bytecodes.INDYMETH_INSN: {
          v += 5;
          break;
        }
        case Bytecodes.TYPE_INSN:
          v += 3;
          break;
        case Bytecodes.IINC_INSN: {
          final String signature = getVarInstructionTypeSignature(opcode);
          if (signature != null) {
            localVariableInstructionFound(opcode, myCode[v + 1] & 0xFF, signature);
          }
          v += 3;
          break;
        }
        // case MANA_INSN:
        default:
          v += 4;
          break;
      }
    }
  }

  private int readUnsignedShort(final int index) {
    final byte[] b = myCode;
    return ((b[index] & 0xFF) << 8) | (b[index + 1] & 0xFF);
  }

  public int readInt(final int index) {
    final byte[] b = myCode;
    return ((b[index] & 0xFF) << 24) | ((b[index + 1] & 0xFF) << 16)
           | ((b[index + 2] & 0xFF) << 8) | (b[index + 3] & 0xFF);
  }

  protected void localVariableInstructionFound(int opcode, int slot, String typeSignature) {
    // override to perform actions
  }

  @Nullable
  private static String getVarInstructionTypeSignature(int opcode) {
    switch (opcode) {
      case Bytecodes.ILOAD:
      case Bytecodes.ILOAD_0:
      case Bytecodes.ILOAD_1:
      case Bytecodes.ILOAD_2:
      case Bytecodes.ILOAD_3:
      case Bytecodes.ISTORE:
      case Bytecodes.ISTORE_0:
      case Bytecodes.ISTORE_1:
      case Bytecodes.ISTORE_2:
      case Bytecodes.ISTORE_3:
      case Bytecodes.IINC:
        return "I";

      case Bytecodes.LLOAD:
      case Bytecodes.LLOAD_0:
      case Bytecodes.LLOAD_1:
      case Bytecodes.LLOAD_2:
      case Bytecodes.LLOAD_3:
      case Bytecodes.LSTORE:
      case Bytecodes.LSTORE_0:
      case Bytecodes.LSTORE_1:
      case Bytecodes.LSTORE_2:
      case Bytecodes.LSTORE_3:
        return "J"; // long

      case Bytecodes.FLOAD:
      case Bytecodes.FLOAD_0:
      case Bytecodes.FLOAD_1:
      case Bytecodes.FLOAD_2:
      case Bytecodes.FLOAD_3:
      case Bytecodes.FSTORE:
      case Bytecodes.FSTORE_0:
      case Bytecodes.FSTORE_1:
      case Bytecodes.FSTORE_2:
      case Bytecodes.FSTORE_3:
        return "F"; // float

      case Bytecodes.DLOAD:
      case Bytecodes.DLOAD_0:
      case Bytecodes.DLOAD_1:
      case Bytecodes.DLOAD_2:
      case Bytecodes.DLOAD_3:
      case Bytecodes.DSTORE:
      case Bytecodes.DSTORE_0:
      case Bytecodes.DSTORE_1:
      case Bytecodes.DSTORE_2:
      case Bytecodes.DSTORE_3:
        return "D"; // double

      case Bytecodes.ALOAD:
      case Bytecodes.ALOAD_0:
      case Bytecodes.ALOAD_1:
      case Bytecodes.ALOAD_2:
      case Bytecodes.ALOAD_3:
      case Bytecodes.ASTORE:
      case Bytecodes.ASTORE_0:
      case Bytecodes.ASTORE_1:
      case Bytecodes.ASTORE_2:
      case Bytecodes.ASTORE_3:
        return "L"; // object ref
    }
    return null;
  }
}
