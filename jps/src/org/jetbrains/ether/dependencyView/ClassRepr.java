package org.jetbrains.ether.dependencyView;

import org.jetbrains.ether.RW;

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
public class ClassRepr extends Proto {

    public final StringCache.S fileName;
    public final TypeRepr.AbstractType superClass;
    public final Set<TypeRepr.AbstractType> interfaces;
    public final Set<TypeRepr.AbstractType> nestedClasses;
    public final FoxyMap<StringCache.S, FieldRepr> fields;
    public final FoxyMap<StringCache.S, MethodRepr> methods;

    private static FoxyMap.CollectionConstructor<FieldRepr> fieldListConstructor = new FoxyMap.CollectionConstructor<FieldRepr> () {
        public Collection<FieldRepr> create() {
            return new LinkedList<FieldRepr>();
        }
    };

    private static FoxyMap.CollectionConstructor<MethodRepr> methodListConstructor = new FoxyMap.CollectionConstructor<MethodRepr> () {
        public Collection<MethodRepr> create() {
            return new LinkedList<MethodRepr>();
        }
    };

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
                return null;  //To change body of implemented methods use File | Settings | File Templates.
            }

            public Difference.Specifier<MethodRepr> methods() {
                return null;  //To change body of implemented methods use File | Settings | File Templates.
            }

            public int base() {
                return d;
            }
        };
    }

    public boolean differentiate(final ClassRepr past) {
        boolean incremental = true;

        /*for (List<FieldRepr> fs : past.fields.values()) {
            for (FieldRepr pastField : fs)
                if ((pastField.access & (Opcodes.ACC_FINAL | Opcodes.ACC_STATIC)) > 0) {
                    for (List<FieldRepr> pfs : fields.values())
                        for (FieldRepr presentField : pfs) {
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
*/
        return incremental;
    }

    public void updateClassUsages(final Set<UsageRepr.Usage> s) {
        superClass.updateClassUsages(s);

        for (TypeRepr.AbstractType t : interfaces) {
            t.updateClassUsages(s);
        }

        for (MethodRepr m : methods.foxyValues()) {
            m.updateClassUsages(s);
        }

        for (FieldRepr f : fields.foxyValues()) {
            f.updateClassUsages(s);
        }
    }

    public ClassRepr(final int a, final StringCache.S fn, final StringCache.S n, final StringCache.S sig, final String sup, final String[] i, final Collection<String> ns, final Collection<FieldRepr> f, final Collection<MethodRepr> m) {
        super(a, sig, n);
        fileName = fn;
        superClass = TypeRepr.createClassType(sup);
        interfaces = (Set<TypeRepr.AbstractType>) TypeRepr.createClassType(i, new HashSet<TypeRepr.AbstractType>());
        nestedClasses = (Set<TypeRepr.AbstractType>) TypeRepr.createClassType(ns, new HashSet<TypeRepr.AbstractType>());
        fields = new FoxyMap<StringCache.S, FieldRepr>(fieldListConstructor);
        methods = new FoxyMap<StringCache.S, MethodRepr>(methodListConstructor);

        for (FieldRepr fr : f) {
            fields.put(fr.name, fr);
        }

        for (MethodRepr mr : m) {
            methods.put(mr.name, mr);
        }
    }

    public ClassRepr(final BufferedReader r) {
        super(r);
        fileName = StringCache.get(RW.readString(r));
        superClass = TypeRepr.reader.read(r);
        interfaces = (Set<TypeRepr.AbstractType>) RW.readMany(r, TypeRepr.reader, new HashSet<TypeRepr.AbstractType>());
        nestedClasses = (Set<TypeRepr.AbstractType>) RW.readMany(r, TypeRepr.reader, new HashSet<TypeRepr.AbstractType>());

        fields = new FoxyMap<StringCache.S, FieldRepr>(fieldListConstructor);
        for (FieldRepr fr : RW.readMany(r, FieldRepr.reader, new LinkedList<FieldRepr>())) {
            fields.put(fr.name, fr);
        }

        methods = new FoxyMap<StringCache.S, MethodRepr>(methodListConstructor);
        for (MethodRepr mr : RW.readMany(r, MethodRepr.reader, new LinkedList<MethodRepr>())) {
            methods.put(mr.name, mr);
        }
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
        RW.writeln(w, fields.foxyValues());
        RW.writeln(w, methods.foxyValues());
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
