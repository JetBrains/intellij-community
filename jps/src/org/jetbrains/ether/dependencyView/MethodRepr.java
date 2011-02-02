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
    private static String[] dummy = new String[0];

    public final String name;
    public final String signature;
    public final Type returnType;
    public final Type[] argTypes;
    public final String[] exceptions;

    private String descr;

    public MethodRepr (final String n, final String s, final String d, final String[] e) {
        name = n;
        returnType = Type.getReturnType(d);
        argTypes = Type.getArgumentTypes(d);
        exceptions = e;
        descr = d;
        signature = s;
    }

    public MethodRepr (final BufferedReader r) {
        name = RW.readString(r);

        final String s = RW.readString(r);

        signature = s.length() == 0 ? null : s;

        descr = RW.readString(r);
        exceptions = RW.readMany(r, RW.myStringConstructor, new ArrayList<String>()).toArray(dummy);

        argTypes = Type.getArgumentTypes(descr);
        returnType = Type.getReturnType(descr);
    }

    public void write(final BufferedWriter w) {
        RW.writeln(w, name);
        RW.writeln(w, signature);
        RW.writeln(w, descr);
        RW.writeln(w, exceptions, RW.fromString);
    }

    public int compareTo(Object o) {
        return 0;
    }

    public static RW.Constructor<MethodRepr> constructor = new RW.Constructor<MethodRepr> () {
        public MethodRepr read(final BufferedReader r) {
            return new MethodRepr (r);
        }
    };
}
