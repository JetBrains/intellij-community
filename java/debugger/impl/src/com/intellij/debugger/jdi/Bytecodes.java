/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

/**
 * As defined in ADM4 library
 */
class Bytecodes {
    /**
     * Pseudo access flag to distinguish between the synthetic attribute and
     * the synthetic access flag.
     */
    static final int ACC_SYNTHETIC_ATTRIBUTE = 0x40000;

    /**
     * The type of instructions without any argument.
     */
    static final int NOARG_INSN = 0;

    /**
     * The type of instructions with an signed byte argument.
     */
    static final int SBYTE_INSN = 1;

    /**
     * The type of instructions with an signed short argument.
     */
    static final int SHORT_INSN = 2;

    /**
     * The type of instructions with a local variable index argument.
     */
    static final int VAR_INSN = 3;

    /**
     * The type of instructions with an implicit local variable index argument.
     */
    static final int IMPLVAR_INSN = 4;

    /**
     * The type of instructions with a type descriptor argument.
     */
    static final int TYPE_INSN = 5;

    /**
     * The type of field and method invocations instructions.
     */
    static final int FIELDORMETH_INSN = 6;

    /**
     * The type of the INVOKEINTERFACE/INVOKEDYNAMIC instruction.
     */
    static final int ITFMETH_INSN = 7;

    /**
     * The type of the INVOKEDYNAMIC instruction.
     */
    static final int INDYMETH_INSN = 8;

    /**
     * The type of instructions with a 2 bytes bytecode offset label.
     */
    static final int LABEL_INSN = 9;

    /**
     * The type of instructions with a 4 bytes bytecode offset label.
     */
    static final int LABELW_INSN = 10;

    /**
     * The type of the LDC instruction.
     */
    static final int LDC_INSN = 11;

    /**
     * The type of the LDC_W and LDC2_W instructions.
     */
    static final int LDCW_INSN = 12;

    /**
     * The type of the IINC instruction.
     */
    static final int IINC_INSN = 13;

    /**
     * The type of the TABLESWITCH instruction.
     */
    static final int TABL_INSN = 14;

    /**
     * The type of the LOOKUPSWITCH instruction.
     */
    static final int LOOK_INSN = 15;

    /**
     * The type of the MULTIANEWARRAY instruction.
     */
    static final int MANA_INSN = 16;

    /**
     * The type of the WIDE instruction.
     */
    static final int WIDE_INSN = 17;

    /**
     * The instruction types of all JVM opcodes.
     */
    static final byte[] TYPE;

    /**
     * The type of CONSTANT_Class constant pool items.
     */
    static final int CLASS = 7;

    /**
     * The type of CONSTANT_Fieldref constant pool items.
     */
    static final int FIELD = 9;

    /**
     * The type of CONSTANT_Methodref constant pool items.
     */
    static final int METH = 10;

    /**
     * The type of CONSTANT_InterfaceMethodref constant pool items.
     */
    static final int IMETH = 11;

    /**
     * The type of CONSTANT_String constant pool items.
     */
    static final int STR = 8;

    /**
     * The type of CONSTANT_Integer constant pool items.
     */
    static final int INT = 3;

    /**
     * The type of CONSTANT_Float constant pool items.
     */
    static final int FLOAT = 4;

    /**
     * The type of CONSTANT_Long constant pool items.
     */
    static final int LONG = 5;

    /**
     * The type of CONSTANT_Double constant pool items.
     */
    static final int DOUBLE = 6;

    /**
     * The type of CONSTANT_NameAndType constant pool items.
     */
    static final int NAME_TYPE = 12;

    /**
     * The type of CONSTANT_Utf8 constant pool items.
     */
    static final int UTF8 = 1;

    /**
     * The type of CONSTANT_MethodType constant pool items.
     */
    static final int MTYPE = 16;

    /**
     * The type of CONSTANT_MethodHandle constant pool items.
     */
    static final int HANDLE = 15;

    /**
     * The type of CONSTANT_InvokeDynamic constant pool items.
     */
    static final int INDY = 18;

    /**
     * The base value for all CONSTANT_MethodHandle constant pool items.
     * Internally, ASM store the 9 variations of CONSTANT_MethodHandle into
     * 9 different items.
     */
    static final int HANDLE_BASE = 20;

    /**
     * Normal type Item stored in the ClassWriter {@link ClassWriter#typeTable},
     * instead of the constant pool, in order to avoid clashes with normal
     * constant pool items in the ClassWriter constant pool's hash table.
     */
    static final int TYPE_NORMAL = 30;

    /**
     * Uninitialized type Item stored in the ClassWriter
     * {@link ClassWriter#typeTable}, instead of the constant pool, in order to
     * avoid clashes with normal constant pool items in the ClassWriter constant
     * pool's hash table.
     */
    static final int TYPE_UNINIT = 31;

    /**
     * Merged type Item stored in the ClassWriter {@link ClassWriter#typeTable},
     * instead of the constant pool, in order to avoid clashes with normal
     * constant pool items in the ClassWriter constant pool's hash table.
     */
    static final int TYPE_MERGED = 32;

    /**
     * The type of BootstrapMethods items. These items are stored in a
     * special class attribute named BootstrapMethods and
     * not in the constant pool.
     */
    static final int BSM = 33;

  /**
     * Computes the instruction types of JVM opcodes.
     */
    static {
        int i;
        byte[] b = new byte[220];
        String s = "AAAAAAAAAAAAAAAABCLMMDDDDDEEEEEEEEEEEEEEEEEEEEAAAAAAAADD"
                + "DDDEEEEEEEEEEEEEEEEEEEEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
                + "AAAAAAAAAAAAAAAAANAAAAAAAAAAAAAAAAAAAAJJJJJJJJJJJJJJJJDOPAA"
                + "AAAAGGGGGGGHIFBFAAFFAARQJJKKJJJJJJJJJJJJJJJJJJ";
        for (i = 0; i < b.length; ++i) {
            b[i] = (byte) (s.charAt(i) - 'A');
        }
        TYPE = b;
    }


  static final int NOP = 0; // visitInsn
  static final int ACONST_NULL = 1; // -
  static final int ICONST_M1 = 2; // -
  static final int ICONST_0 = 3; // -
  static final int ICONST_1 = 4; // -
  static final int ICONST_2 = 5; // -
  static final int ICONST_3 = 6; // -
  static final int ICONST_4 = 7; // -
  static final int ICONST_5 = 8; // -
  static final int LCONST_0 = 9; // -
  static final int LCONST_1 = 10; // -
  static final int FCONST_0 = 11; // -
  static final int FCONST_1 = 12; // -
  static final int FCONST_2 = 13; // -
  static final int DCONST_0 = 14; // -
  static final int DCONST_1 = 15; // -
  static final int BIPUSH = 16; // visitIntInsn
  static final int SIPUSH = 17; // -
  static final int LDC = 18; // visitLdcInsn
  static final int LDC_W = 19; // -
  static final int LDC2_W = 20; // -
  static final int ILOAD = 21; // visitVarInsn
  static final int LLOAD = 22; // -
  static final int FLOAD = 23; // -
  static final int DLOAD = 24; // -
  static final int ALOAD = 25; // -
  static final int ILOAD_0 = 26; // -
  static final int ILOAD_1 = 27; // -
  static final int ILOAD_2 = 28; // -
  static final int ILOAD_3 = 29; // -
  static final int LLOAD_0 = 30; // -
  static final int LLOAD_1 = 31; // -
  static final int LLOAD_2 = 32; // -
  static final int LLOAD_3 = 33; // -
  static final int FLOAD_0 = 34; // -
  static final int FLOAD_1 = 35; // -
  static final int FLOAD_2 = 36; // -
  static final int FLOAD_3 = 37; // -
  static final int DLOAD_0 = 38; // -
  static final int DLOAD_1 = 39; // -
  static final int DLOAD_2 = 40; // -
  static final int DLOAD_3 = 41; // -
  static final int ALOAD_0 = 42; // -
  static final int ALOAD_1 = 43; // -
  static final int ALOAD_2 = 44; // -
  static final int ALOAD_3 = 45; // -
  static final int IALOAD = 46; // visitInsn
  static final int LALOAD = 47; // -
  static final int FALOAD = 48; // -
  static final int DALOAD = 49; // -
  static final int AALOAD = 50; // -
  static final int BALOAD = 51; // -
  static final int CALOAD = 52; // -
  static final int SALOAD = 53; // -
  static final int ISTORE = 54; // visitVarInsn
  static final int LSTORE = 55; // -
  static final int FSTORE = 56; // -
  static final int DSTORE = 57; // -
  static final int ASTORE = 58; // -
  static final int ISTORE_0 = 59; // -
  static final int ISTORE_1 = 60; // -
  static final int ISTORE_2 = 61; // -
  static final int ISTORE_3 = 62; // -
  static final int LSTORE_0 = 63; // -
  static final int LSTORE_1 = 64; // -
  static final int LSTORE_2 = 65; // -
  static final int LSTORE_3 = 66; // -
  static final int FSTORE_0 = 67; // -
  static final int FSTORE_1 = 68; // -
  static final int FSTORE_2 = 69; // -
  static final int FSTORE_3 = 70; // -
  static final int DSTORE_0 = 71; // -
  static final int DSTORE_1 = 72; // -
  static final int DSTORE_2 = 73; // -
  static final int DSTORE_3 = 74; // -
  static final int ASTORE_0 = 75; // -
  static final int ASTORE_1 = 76; // -
  static final int ASTORE_2 = 77; // -
  static final int ASTORE_3 = 78; // -
  static final int IASTORE = 79; // visitInsn
  static final int LASTORE = 80; // -
  static final int FASTORE = 81; // -
  static final int DASTORE = 82; // -
  static final int AASTORE = 83; // -
  static final int BASTORE = 84; // -
  static final int CASTORE = 85; // -
  static final int SASTORE = 86; // -
  static final int POP = 87; // -
  static final int POP2 = 88; // -
  static final int DUP = 89; // -
  static final int DUP_X1 = 90; // -
  static final int DUP_X2 = 91; // -
  static final int DUP2 = 92; // -
  static final int DUP2_X1 = 93; // -
  static final int DUP2_X2 = 94; // -
  static final int SWAP = 95; // -
  static final int IADD = 96; // -
  static final int LADD = 97; // -
  static final int FADD = 98; // -
  static final int DADD = 99; // -
  static final int ISUB = 100; // -
  static final int LSUB = 101; // -
  static final int FSUB = 102; // -
  static final int DSUB = 103; // -
  static final int IMUL = 104; // -
  static final int LMUL = 105; // -
  static final int FMUL = 106; // -
  static final int DMUL = 107; // -
  static final int IDIV = 108; // -
  static final int LDIV = 109; // -
  static final int FDIV = 110; // -
  static final int DDIV = 111; // -
  static final int IREM = 112; // -
  static final int LREM = 113; // -
  static final int FREM = 114; // -
  static final int DREM = 115; // -
  static final int INEG = 116; // -
  static final int LNEG = 117; // -
  static final int FNEG = 118; // -
  static final int DNEG = 119; // -
  static final int ISHL = 120; // -
  static final int LSHL = 121; // -
  static final int ISHR = 122; // -
  static final int LSHR = 123; // -
  static final int IUSHR = 124; // -
  static final int LUSHR = 125; // -
  static final int IAND = 126; // -
  static final int LAND = 127; // -
  static final int IOR = 128; // -
  static final int LOR = 129; // -
  static final int IXOR = 130; // -
  static final int LXOR = 131; // -
  static final int IINC = 132; // visitIincInsn
  static final int I2L = 133; // visitInsn
  static final int I2F = 134; // -
  static final int I2D = 135; // -
  static final int L2I = 136; // -
  static final int L2F = 137; // -
  static final int L2D = 138; // -
  static final int F2I = 139; // -
  static final int F2L = 140; // -
  static final int F2D = 141; // -
  static final int D2I = 142; // -
  static final int D2L = 143; // -
  static final int D2F = 144; // -
  static final int I2B = 145; // -
  static final int I2C = 146; // -
  static final int I2S = 147; // -
  static final int LCMP = 148; // -
  static final int FCMPL = 149; // -
  static final int FCMPG = 150; // -
  static final int DCMPL = 151; // -
  static final int DCMPG = 152; // -
  static final int IFEQ = 153; // visitJumpInsn
  static final int IFNE = 154; // -
  static final int IFLT = 155; // -
  static final int IFGE = 156; // -
  static final int IFGT = 157; // -
  static final int IFLE = 158; // -
  static final int IF_ICMPEQ = 159; // -
  static final int IF_ICMPNE = 160; // -
  static final int IF_ICMPLT = 161; // -
  static final int IF_ICMPGE = 162; // -
  static final int IF_ICMPGT = 163; // -
  static final int IF_ICMPLE = 164; // -
  static final int IF_ACMPEQ = 165; // -
  static final int IF_ACMPNE = 166; // -
  static final int GOTO = 167; // -
  static final int JSR = 168; // -
  static final int RET = 169; // visitVarInsn
  static final int TABLESWITCH = 170; // visiTableSwitchInsn
  static final int LOOKUPSWITCH = 171; // visitLookupSwitch
  static final int IRETURN = 172; // visitInsn
  static final int LRETURN = 173; // -
  static final int FRETURN = 174; // -
  static final int DRETURN = 175; // -
  static final int ARETURN = 176; // -
  static final int RETURN = 177; // -
  static final int GETSTATIC = 178; // visitFieldInsn
  static final int PUTSTATIC = 179; // -
  static final int GETFIELD = 180; // -
  static final int PUTFIELD = 181; // -
  static final int INVOKEVIRTUAL = 182; // visitMethodInsn
  static final int INVOKESPECIAL = 183; // -
  static final int INVOKESTATIC = 184; // -
  static final int INVOKEINTERFACE = 185; // -
  static final int INVOKEDYNAMIC = 186; // visitInvokeDynamicInsn
  static final int NEW = 187; // visitTypeInsn
  static final int NEWARRAY = 188; // visitIntInsn
  static final int ANEWARRAY = 189; // visitTypeInsn
  static final int ARRAYLENGTH = 190; // visitInsn
  static final int ATHROW = 191; // -
  static final int CHECKCAST = 192; // visitTypeInsn
  static final int INSTANCEOF = 193; // -
  static final int MONITORENTER = 194; // visitInsn
  static final int MONITOREXIT = 195; // -
  static final int WIDE = 196; // NOT VISITED
  static final int MULTIANEWARRAY = 197; // visitMultiANewArrayInsn
  static final int IFNULL = 198; // visitJumpInsn
  static final int IFNONNULL = 199; // -
  static final int GOTO_W = 200; // -
  static final int JSR_W = 201; // -

}
