package org.jetbrains.ether.dependencyView;

import org.jetbrains.ether.RW;
import org.objectweb.asm.Opcodes;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.util.ArrayList;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 01.02.11
 * Time: 4:54
 * To change this template use File | Settings | File Templates.
 */
public class ClassRepr implements RW.Writable {
    private FieldRepr[] dummyFields = new FieldRepr[0];
    private MethodRepr[] dummyMethods = new MethodRepr[0];
    private TypeRepr.AbstractType[] dummyTypes = new TypeRepr.AbstractType[0];

    public final StringCache.S fileName;
    public final StringCache.S name;
    public final TypeRepr.AbstractType superClass;
    public final TypeRepr.AbstractType[] interfaces;
    public final TypeRepr.AbstractType[] nestedClasses;
    public final FieldRepr[] fields;
    public final MethodRepr[] methods;
    public final String signature;

    public boolean differentiate(final ClassRepr past) {
        boolean incremental = true;

        for (FieldRepr pastField : past.fields) {
            if ((pastField.access & (Opcodes.ACC_FINAL | Opcodes.ACC_STATIC)) > 0) {
                for (FieldRepr presentField : fields) {
                    if (presentField.name.equals(pastField.name)) {
                        if (presentField.access != pastField.access ||
                                (presentField.value != null && pastField.value != null &&
                                        !presentField.value.equals(pastField.value)
                                ) ||
                                (presentField.value != pastField.value && (presentField.value == null || pastField.value == null))
                                ) {
                            incremental = false;
                        }
                    }
                }
            }
        }

        return incremental;
    }

    public void updateClassUsages(final Set<UsageRepr.Usage> s) {
        superClass.updateClassUsages(s);

        for (TypeRepr.AbstractType t : interfaces) {
            t.updateClassUsages(s);
        }

        for (MethodRepr m : methods) {
            m.updateClassUsages(s);
        }

        for (FieldRepr f : fields) {
            f.updateClassUsages(s);
        }
    }

    public ClassRepr(final StringCache.S fn, final StringCache.S n, final String sig, final String sup, final String[] i, final String[] ns, final FieldRepr[] f, final MethodRepr[] m) {
        fileName = fn;
        name = n;
        superClass = TypeRepr.createClassType(sup);
        interfaces = TypeRepr.createClassType(i);
        nestedClasses = TypeRepr.createClassType(ns);
        fields = f;
        methods = m;
        signature = sig;
    }

    public ClassRepr(final BufferedReader r) {
        fileName = StringCache.get(RW.readString(r));
        name = StringCache.get(RW.readString(r));

        final String s = RW.readString(r);

        signature = s.length() == 0 ? null : s;

        superClass = TypeRepr.reader.read(r);
        interfaces = RW.readMany(r, TypeRepr.reader, new ArrayList<TypeRepr.AbstractType>()).toArray(dummyTypes);
        nestedClasses = RW.readMany(r, TypeRepr.reader, new ArrayList<TypeRepr.AbstractType>()).toArray(dummyTypes);
        fields = RW.readMany(r, FieldRepr.reader, new ArrayList<FieldRepr>()).toArray(dummyFields);
        methods = RW.readMany(r, MethodRepr.reader, new ArrayList<MethodRepr>()).toArray(dummyMethods);
    }

    public static RW.Reader<ClassRepr> reader = new RW.Reader<ClassRepr>() {
        public ClassRepr read(final BufferedReader r) {
            return new ClassRepr(r);
        }
    };

    public void write(final BufferedWriter w) {
        RW.writeln(w, fileName.value);
        RW.writeln(w, name.value);
        RW.writeln(w, signature);
        superClass.write(w);
        RW.writeln(w, interfaces, TypeRepr.fromAbstractType);
        RW.writeln(w, nestedClasses, TypeRepr.fromAbstractType);
        RW.writeln(w, fields, RW.fromWritable);
        RW.writeln(w, methods, RW.fromWritable);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ClassRepr classRepr = (ClassRepr) o;

        if (fileName != null ? !fileName.equals(classRepr.fileName) : classRepr.fileName != null) return false;
        if (name != null ? !name.equals(classRepr.name) : classRepr.name != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = fileName != null ? fileName.hashCode() : 0;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        return result;
    }
}
