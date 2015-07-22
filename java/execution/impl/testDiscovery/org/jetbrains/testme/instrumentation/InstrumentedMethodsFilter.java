package org.jetbrains.testme.instrumentation;

import org.jetbrains.org.objectweb.asm.Opcodes;

public class InstrumentedMethodsFilter {
    private final String myClassName;
    private boolean myEnum;

    public InstrumentedMethodsFilter(String className) {
        myClassName = className;
    }

    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        myEnum = (access & Opcodes.ACC_ENUM) != 0;
    }

    public boolean shouldVisitMethod(final int access,
                                     final String name,
                                     final String desc,
                                     final String signature,
                                     final String[] exceptions) {
        if ((access & Opcodes.ACC_BRIDGE) != 0) return false; //try to skip bridge methods
        if ((access & Opcodes.ACC_ABSTRACT) != 0) return false; //skip abstracts; do not include interfaces without non-abstract methods in result
        if ("<clinit>".equals(name) || //static initializer
            ((access & Opcodes.ACC_SYNTHETIC) != 0 && name.startsWith("access$")) || // synthetic access method
            name.equals("<init>") && signature != null && signature.equals("()V") // default constructor
          ) {
            // todo skip only trivial default constructor
            return false;
        }

        if (myEnum && isDefaultEnumMethod(name, desc, signature, myClassName)) {
            return false;
        }
        return true;
    }

    private static boolean isDefaultEnumMethod(String name, String desc, String signature, String className) {
        return name.equals("values") && desc.equals("()[L" + className + ";") ||
                name.equals("valueOf") && desc.equals("(Ljava/lang/String;)L" + className + ";") ||
                name.equals("<init>") && signature != null && signature.equals("()V");
    }
}
