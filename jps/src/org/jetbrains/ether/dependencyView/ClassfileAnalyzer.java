package org.jetbrains.ether.dependencyView;

import org.jetbrains.ether.Pair;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.EmptyVisitor;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;

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

        private void processSignature(final String sig) {
            if (sig != null)
                new SignatureReader(sig).accept(signatureCrawler);
        }

        private final SignatureVisitor signatureCrawler = new SignatureVisitor() {
            public void visitFormalTypeParameter(String name) {
            }

            public SignatureVisitor visitClassBound() {
                return this;
            }

            public SignatureVisitor visitInterfaceBound() {
                return this;
            }

            public SignatureVisitor visitSuperclass() {
                return this;
            }

            public SignatureVisitor visitInterface() {
                return this;
            }

            public SignatureVisitor visitParameterType() {
                return this;
            }

            public SignatureVisitor visitReturnType() {
                return this;
            }

            public SignatureVisitor visitExceptionType() {
                return this;
            }

            public void visitBaseType(char descriptor) {
            }

            public void visitTypeVariable(String name) {
            }

            public SignatureVisitor visitArrayType() {
                return this;
            }

            public void visitInnerClassType(String name) {
            }

            public void visitTypeArgument() {
            }

            public SignatureVisitor visitTypeArgument(char wildcard) {
                return this;
            }

            public void visitEnd() {
            }

            public void visitClassType(String name) {
                usages.add(UsageRepr.createClassUsage(name));
            }
        };

        Boolean takeIntoAccount = false;

        final StringCache.S fileName;
        int access;
        StringCache.S name;
        String superClass;
        String[] interfaces;
        String signature;
        StringCache.S sourceFile;

        final Set<MethodRepr> methods = new HashSet<MethodRepr>();
        final Set<FieldRepr> fields = new HashSet<FieldRepr>();
        final List<String> nestedClasses = new ArrayList<String>();
        final Set<UsageRepr.Usage> usages = new HashSet<UsageRepr.Usage>();

        public ClassCrawler(final StringCache.S fn) {
            fileName = fn;
        }

        private boolean notPrivate(final int access) {
            return (access & Opcodes.ACC_PRIVATE) == 0;
        }

        public Pair<ClassRepr, Set<UsageRepr.Usage>> getResult() {
            final ClassRepr repr = takeIntoAccount ?
                    new ClassRepr(access, sourceFile, fileName, name, StringCache.get(signature), superClass, interfaces, nestedClasses, fields, methods) : null;

            if (repr != null) {
                repr.updateClassUsages(usages);
            }

            return new Pair<ClassRepr, Set<UsageRepr.Usage>>(repr, usages);
        }

        @Override
        public void visit(int version, int a, String n, String sig, String s, String[] i) {
            takeIntoAccount = notPrivate(a);

            access = a;
            name = StringCache.get(n);
            signature = sig;
            superClass = s;
            interfaces = i;

            processSignature(sig);
        }

        @Override
        public void visitSource(String source, String debug) {
            sourceFile = StringCache.get(source);
        }

        @Override
        public FieldVisitor visitField(int access, String n, String desc, String signature, Object value) {
            processSignature(signature);

            if (notPrivate(access)) {
                fields.add(new FieldRepr(access, n, desc, signature, value));
            }

            return null;
        }

        @Override
        public MethodVisitor visitMethod(int access, String n, String desc, String signature, String[] exceptions) {
            processSignature(signature);

            if (notPrivate(access)) {
                methods.add(new MethodRepr(access, n, signature, desc, exceptions));
            }

            return new EmptyVisitor() {
                @Override
                public void visitMultiANewArrayInsn(String desc, int dims) {
                    TypeRepr.getType(desc).updateClassUsages(usages);
                    super.visitMultiANewArrayInsn(desc, dims);
                }

                @Override
                public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
                    processSignature(signature);
                    TypeRepr.getType(desc).updateClassUsages(usages);
                    super.visitLocalVariable(name, desc, signature, start, end, index);
                }

                @Override
                public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
                    if (type != null)
                        TypeRepr.createClassType(type).updateClassUsages(usages);
                    super.visitTryCatchBlock(start, end, handler, type);
                }

                @Override
                public void visitTypeInsn(int opcode, String type) {
                    TypeRepr.createClassType(type).updateClassUsages(usages);
                    super.visitTypeInsn(opcode, type);
                }

                @Override
                public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                    usages.add(UsageRepr.createFieldUsage(name, owner, desc));
                    super.visitFieldInsn(opcode, owner, name, desc);
                }

                @Override
                public void visitMethodInsn(int opcode, String owner, String name, String desc) {
                    usages.add(UsageRepr.createMethodUsage(name, owner, desc));
                    super.visitMethodInsn(opcode, owner, name, desc);
                }
            };
        }

        @Override
        public void visitInnerClass(String name, String outerName, String innerName, int access) {
            if (outerName != null && outerName.equals(name) && notPrivate(access)) {
                nestedClasses.add(innerName);
            }
        }
    }

    public static Pair<ClassRepr, Set<UsageRepr.Usage>> analyze(final StringCache.S fileName, final ClassReader cr) {
        final ClassCrawler visitor = new ClassCrawler(fileName);

        cr.accept(visitor, 0);

        return visitor.getResult();
    }
}