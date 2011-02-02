package org.jetbrains.ether.dependencyView;

import com.sun.tools.javac.util.Pair;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.EmptyVisitor;

import javax.swing.border.EmptyBorder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 31.01.11
 * Time: 2:00
 * To change this template use File | Settings | File Templates.
 */

public class ClassfileAnalyzer {

    private static class ClassCrawler extends EmptyVisitor {
        FieldRepr[] dummyFields = new FieldRepr[0];
        MethodRepr[] dummyMethods = new MethodRepr[0];
        String[] dummyStrings = new String[0];

        Boolean takeIntoAccount = false;

        final String fileName;
        String name;
        String superClass;
        String[] interfaces;
        String signature;

        List<MethodRepr> methods = new ArrayList<MethodRepr>();
        List<FieldRepr> fields = new ArrayList<FieldRepr>();
        List<String> nestedClasses = new ArrayList<String>();

        Set<Usage> usages = new HashSet<Usage>();

        public ClassCrawler(final String fn) {
            fileName = fn;
        }

        private boolean notPrivate(final int access) {
            return (access & Opcodes.ACC_PRIVATE) == 0;
        }

        public Pair<ClassRepr, Set<Usage>> getResult() {
            final ClassRepr repr = takeIntoAccount ?
                    new ClassRepr(fileName, name, signature, superClass, interfaces, nestedClasses.toArray(dummyStrings), fields.toArray(dummyFields), methods.toArray(dummyMethods)) : null;

            return new Pair<ClassRepr, Set<Usage>>(repr, usages);
        }

        public void visit(int version, int access, String n, String sig, String s, String[] i) {
            takeIntoAccount = notPrivate(access);

            name = n;
            signature = sig;
            superClass = s;
            interfaces = i;
        }

        public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {

            if (notPrivate(access)) {
                fields.add(new FieldRepr(name, desc, signature));
            }

            return null;
        }

        public void visitFieldInsn(int opcode, String owner, String name, String desc) {
            usages.add(Usage.createFieldUsage(name, owner, desc));
        }

        public void visitMethodInsn(int opcode, String owner, String name, String desc) {
            usages.add(Usage.createMethodUsage(name, owner, desc));
        }

        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {

            if (notPrivate(access)) {
                methods.add(new MethodRepr(name, signature, desc, exceptions));
            }

            return new EmptyVisitor() {
                public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                    usages.add(Usage.createFieldUsage(name, owner, desc));
                }

                public void visitMethodInsn(int opcode, String owner, String name, String desc) {
                    usages.add(Usage.createMethodUsage(name, owner, desc));
                }
            };
        }

        public void visitInnerClass(String name, String outerName, String innerName, int access) {
            if (outerName != null && outerName.equals(name) && notPrivate(access)) {
                nestedClasses.add(innerName);
            }
        }
    }

    public static Pair<ClassRepr,Set<Usage>> analyze(final String fileName, final ClassReader cr) {
        final ClassCrawler visitor = new ClassCrawler(fileName);

        cr.accept(visitor, 0);

        return visitor.getResult();
    }
}