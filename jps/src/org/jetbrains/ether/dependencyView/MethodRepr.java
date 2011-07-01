package org.jetbrains.ether.dependencyView;

import org.jetbrains.ether.RW;
import org.objectweb.asm.Type;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 01.02.11
 * Time: 5:03
 * To change this template use File | Settings | File Templates.
 */
public class MethodRepr extends ProtoMember {
    private static TypeRepr.AbstractType[] dummyAbstractType = new TypeRepr.AbstractType[0];

    public final TypeRepr.AbstractType[] argumentTypes;
    public final Set<TypeRepr.AbstractType> exceptions;

    public abstract class Diff extends Difference {
        public abstract Specifier<TypeRepr.AbstractType> exceptions();
    }

    @Override
    public Difference difference(Proto past) {
        final int d = super.difference(past).base();
        final Difference.Specifier<TypeRepr.AbstractType> excs = Difference.make(((MethodRepr) past).exceptions, exceptions);

        return new Diff() {
            @Override
            public Specifier<TypeRepr.AbstractType> exceptions() {
                return excs;
            }

            @Override
            public int base() {
                return d;
            }
        };
    }

    public void updateClassUsages(final Set<UsageRepr.Usage> s) {
        type.updateClassUsages(s);

        for (int i = 0; i < argumentTypes.length; i++) {
            argumentTypes[i].updateClassUsages(s);
        }

        if (exceptions != null)
            for (TypeRepr.AbstractType typ : exceptions) {
                typ.updateClassUsages(s);
            }
    }

    public MethodRepr(final int a, final String n, final String s, final String d, final String[] e) {
        super(a, s, StringCache.get(n), TypeRepr.getType(Type.getReturnType(d)));
        exceptions = (Set<TypeRepr.AbstractType>) TypeRepr.createClassType(e, new HashSet<TypeRepr.AbstractType>());
        argumentTypes = TypeRepr.getType(Type.getArgumentTypes(d));
    }

    public MethodRepr(final BufferedReader r) {
        super(r);
        argumentTypes = RW.readMany(r, TypeRepr.reader, new ArrayList<TypeRepr.AbstractType>()).toArray(dummyAbstractType);
        exceptions = (Set<TypeRepr.AbstractType>) RW.readMany(r, TypeRepr.reader, new HashSet<TypeRepr.AbstractType>());
    }

    public void write(final BufferedWriter w) {
        super.write(w);
        RW.writeln(w, argumentTypes, TypeRepr.fromAbstractType);
        RW.writeln(w, exceptions);
    }

    public static RW.Reader<MethodRepr> reader = new RW.Reader<MethodRepr>() {
        public MethodRepr read(final BufferedReader r) {
            return new MethodRepr(r);
        }
    };

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final MethodRepr that = (MethodRepr) o;

        return name .equals(that.name ) &&
               type .equals(that.type) &&
               Arrays.equals(argumentTypes, that.argumentTypes);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * Arrays.hashCode(argumentTypes) + type.hashCode()) + name.hashCode();
    }

    public UsageRepr.Usage createUsage (final StringCache.S owner) {
        final StringBuffer buf = new StringBuffer ();

        buf.append("(");

        for (TypeRepr.AbstractType t : argumentTypes) {
            buf.append(t.getDescr());
        }

        buf.append(")");
        buf.append(type.getDescr());

        return UsageRepr.createMethodUsage(name.value, owner.value, buf.toString());
    }
}
