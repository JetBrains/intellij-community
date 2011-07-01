package org.jetbrains.ether.dependencyView;

import org.jetbrains.ether.RW;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 01.02.11
 * Time: 4:54
 * To change this template use File | Settings | File Templates.
 */
public class ClassRepr extends Proto {
    public final StringCache.S sourceFileName;
    public final StringCache.S fileName;
    public final TypeRepr.AbstractType superClass;
    public final Set<TypeRepr.AbstractType> interfaces;
    public final Set<TypeRepr.AbstractType> nestedClasses;

    private final Set<FieldRepr> fields;
    private final Set<MethodRepr> methods;

    public abstract class Diff extends Difference {
        public abstract Difference.Specifier<TypeRepr.AbstractType> interfaces();

        public abstract Difference.Specifier<TypeRepr.AbstractType> nestedClasses();

        public abstract Difference.Specifier<FieldRepr> fields();

        public abstract Difference.Specifier<MethodRepr> methods();
    }

    public Diff difference(final Proto past) {
        final ClassRepr pastClass = (ClassRepr) past;

        int diff = super.difference(past).base();

        if (!superClass.equals(pastClass.superClass)) {
            diff |= Difference.SUPERCLASS;
        }

        final int d = diff;

        return new Diff() {
            public Difference.Specifier<TypeRepr.AbstractType> interfaces() {
                return Difference.make(pastClass.interfaces, interfaces);
            }

            public Difference.Specifier<TypeRepr.AbstractType> nestedClasses() {
                return Difference.make(pastClass.nestedClasses, nestedClasses);
            }

            public Difference.Specifier<FieldRepr> fields() {
                return Difference.make(pastClass.fields, fields);
            }

            public Difference.Specifier<MethodRepr> methods() {
                return Difference.make(pastClass.methods, methods);
            }

            public int base() {
                return d;
            }
        };
    }

    public boolean differentiate(final ClassRepr past, final Set<StringCache.S> affected) {
        boolean incremental = true;
        final Diff diff = difference(past);

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

    public ClassRepr(final int a, final StringCache.S sn, final StringCache.S fn, final StringCache.S n, final String sig, final String sup, final String[] i, final Collection<String> ns, final Set<FieldRepr> f, final Set<MethodRepr> m) {
        super(a, sig, n);
        fileName = fn;
        sourceFileName = sn;
        superClass = TypeRepr.createClassType(sup);
        interfaces = (Set<TypeRepr.AbstractType>) TypeRepr.createClassType(i, new HashSet<TypeRepr.AbstractType>());
        nestedClasses = (Set<TypeRepr.AbstractType>) TypeRepr.createClassType(ns, new HashSet<TypeRepr.AbstractType>());
        fields = f;
        methods = m;
    }

    public ClassRepr(final BufferedReader r) {
        super(r);
        fileName = StringCache.get(RW.readString(r));
        sourceFileName = null;
        superClass = TypeRepr.reader.read(r);
        interfaces = (Set<TypeRepr.AbstractType>) RW.readMany(r, TypeRepr.reader, new HashSet<TypeRepr.AbstractType>());
        nestedClasses = (Set<TypeRepr.AbstractType>) RW.readMany(r, TypeRepr.reader, new HashSet<TypeRepr.AbstractType>());
        fields = (Set<FieldRepr>) RW.readMany(r, FieldRepr.reader, new HashSet<FieldRepr>());
        methods = (Set<MethodRepr>) RW.readMany(r, MethodRepr.reader, new HashSet<MethodRepr>());
    }

    public static RW.Reader<ClassRepr> reader = new RW.Reader<ClassRepr>() {
        public ClassRepr read(final BufferedReader r) {
            return new ClassRepr(r);
        }
    };

    public void write(final BufferedWriter w) {
        super.write(w);
        RW.writeln(w, fileName.value);
        superClass.write(w);
        RW.writeln(w, interfaces);
        RW.writeln(w, nestedClasses);
        RW.writeln(w, fields);
        RW.writeln(w, methods);
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

    public UsageRepr.Usage createUsage() {
        return UsageRepr.createClassUsage(name);
    }

    public StringCache.S getSourceFileName() {
        return sourceFileName;
    }
}
