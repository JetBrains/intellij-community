package org.jetbrains.ether.dependencyView;

import org.jetbrains.ether.RW;
import org.objectweb.asm.Opcodes;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 01.02.11
 * Time: 4:54
 * To change this template use File | Settings | File Templates.
 */
public class ClassRepr implements RW.Writable {
    public final StringCache.S fileName;
    public final StringCache.S name;
    public final TypeRepr.AbstractType superClass;
    public final Set<TypeRepr.AbstractType> interfaces;
    public final Set<TypeRepr.AbstractType> nestedClasses;
    public final Map<StringCache.S, FieldRepr> fields;
    public final Map<StringCache.S, List<MethodRepr>> methods;
    public final String signature;

    public boolean differentiate(final ClassRepr past) {
        boolean incremental = true;

        for (FieldRepr pastField : past.fields.values()) {
            if ((pastField.access & (Opcodes.ACC_FINAL | Opcodes.ACC_STATIC)) > 0) {
                for (FieldRepr presentField : fields.values()) {
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

        for (List<MethodRepr> ms : methods.values()) {
            for (MethodRepr m : ms)
                m.updateClassUsages(s);
        }

        for (FieldRepr f : fields.values()) {
            f.updateClassUsages(s);
        }
    }

    public ClassRepr(final StringCache.S fn, final StringCache.S n, final String sig, final String sup, final String[] i, final Collection<String> ns, final Collection<FieldRepr> f, final Collection<MethodRepr> m) {
        fileName = fn;
        name = n;
        superClass = TypeRepr.createClassType(sup);
        interfaces = (Set<TypeRepr.AbstractType>) TypeRepr.createClassType(i, new HashSet<TypeRepr.AbstractType>());
        nestedClasses = (Set<TypeRepr.AbstractType>) TypeRepr.createClassType(ns, new HashSet<TypeRepr.AbstractType>());
        fields = new HashMap<StringCache.S, FieldRepr>();
        methods = new HashMap<StringCache.S, List<MethodRepr>>();

        for (FieldRepr fr : f) {
            fields.put(fr.name, fr);
        }

        for (MethodRepr mr : m) {
            List<MethodRepr> ms = methods.get(mr.name);

            if (ms == null) {
                ms = new LinkedList<MethodRepr>();
                methods.put(mr.name, ms);
            }

            ms.add(mr);
        }

        signature = sig;
    }

    public ClassRepr(final BufferedReader r) {
        fileName = StringCache.get(RW.readString(r));
        name = StringCache.get(RW.readString(r));

        final String s = RW.readString(r);

        signature = s.length() == 0 ? null : s;

        superClass = TypeRepr.reader.read(r);
        interfaces = (Set<TypeRepr.AbstractType>) RW.readMany(r, TypeRepr.reader, new HashSet<TypeRepr.AbstractType>());
        nestedClasses = (Set<TypeRepr.AbstractType>) RW.readMany(r, TypeRepr.reader, new HashSet<TypeRepr.AbstractType>());

        fields = new HashMap<StringCache.S, FieldRepr>();
        for (FieldRepr fr : RW.readMany(r, FieldRepr.reader, new ArrayList<FieldRepr>())) {
            fields.put(fr.name, fr);
        }

        methods = new HashMap<StringCache.S, List<MethodRepr>>();
        for (MethodRepr mr : RW.readMany(r, MethodRepr.reader, new ArrayList<MethodRepr>())) {
            List<MethodRepr> ms = methods.get(mr.name);

            if (ms == null) {
                ms = new LinkedList<MethodRepr>();
                methods.put(mr.name, ms);
            }

            ms.add(mr);
        }
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
        RW.writeln(w, interfaces);
        RW.writeln(w, nestedClasses);
        RW.writeln(w, fields.values());

        final List<MethodRepr> ms = new ArrayList<MethodRepr>();

        for (List<MethodRepr> mr : methods.values()) {
            ms.addAll(mr);
        }

        RW.writeln(w, ms);
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
