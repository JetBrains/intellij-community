package org.jetbrains.ether.dependencyView;

import org.objectweb.asm.*;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 31.01.11
 * Time: 2:00
 * To change this template use File | Settings | File Templates.
 */
public class ClassfileAnalyzer {

    public static class ClassPrinter implements ClassVisitor {
        public void visit(int version, int access, String name,
                          String signature, String superName, String[] interfaces) {
            System.out.println(name + " extends " + superName + " {");
        }

        public void visitSource(String source, String debug) {
        }

        public void visitOuterClass(String owner, String name, String desc) {
        }

        public AnnotationVisitor visitAnnotation(String desc,
                                                 boolean visible) {
            return null;
        }

        public void visitAttribute(Attribute attr) {
        }

        public void visitInnerClass(String name, String outerName,
                                    String innerName, int access) {
        }

        public FieldVisitor visitField(int access, String name, String desc,
                                       String signature, Object value) {
            System.out.println(" " + desc + " " + name);
            return null;
        }

        public MethodVisitor visitMethod(int access, String name,
                                         String desc, String signature, String[] exceptions) {
            System.out.println(" " + name + desc);
            return null;
        }

        public void visitEnd() {
            System.out.println("}");
        }
    }

    public static void analyze(final ClassReader cr) {
        cr.accept(new ClassPrinter (), 0);
    }
}
