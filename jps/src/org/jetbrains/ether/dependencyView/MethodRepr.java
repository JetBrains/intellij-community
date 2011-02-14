package org.jetbrains.ether.dependencyView;

import org.jetbrains.ether.RW;
import org.objectweb.asm.Type;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 01.02.11
 * Time: 5:03
 * To change this template use File | Settings | File Templates.
 */
public class MethodRepr implements RW.Writable {
    private static String[] dummyString = new String[0];
    private static TypeRepr.AbstractType[] dummyAbstractType = new TypeRepr.AbstractType[0];

    public final StringCache.S name;
    public final String signature;
    public final int access;
    public final TypeRepr.AbstractType returnType;
    public final TypeRepr.AbstractType[] argumentTypes;
    public final StringCache.S[] exceptions;

    public MethodRepr (final int a, final String n, final String s, final String d, final String[] e) {
        name = StringCache.get(n);
        exceptions = StringCache.get (e);
        signature = s;
        access = a;
        argumentTypes = TypeRepr.getType(Type.getArgumentTypes(d));
        returnType = TypeRepr.getType(Type.getReturnType(d));
    }

    public MethodRepr (final BufferedReader r) {
        name = StringCache.get (RW.readString(r));
        access = RW.readInt(r);

        final String s = RW.readString(r);

        signature = s.length() == 0 ? null : s;

        argumentTypes = RW.readMany (r, TypeRepr.reader, new ArrayList<TypeRepr.AbstractType>()).toArray(dummyAbstractType);
        returnType = TypeRepr.reader.read(r);

        exceptions = StringCache.get (RW.readMany(r, RW.myStringReader, new ArrayList<String>()).toArray(dummyString));
    }

    public void write(final BufferedWriter w) {
        RW.writeln(w, name.value);
        RW.writeln(w, Integer.toString(access));
        RW.writeln(w, signature);

        RW.writeln(w, argumentTypes, TypeRepr.fromAbstractType);
        returnType.write(w);
        RW.writeln(w, exceptions, StringCache.fromS);
    }

    public static RW.Reader<MethodRepr> reader = new RW.Reader<MethodRepr>() {
        public MethodRepr read(final BufferedReader r) {
            return new MethodRepr (r);
        }
    };
}
