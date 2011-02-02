package org.jetbrains.ether.dependencyView;

import org.jetbrains.ether.RW;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.util.ArrayList;

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
    private String[] dummyStrings = new String[0];

    public final String fileName;
    public final String name;
    public final String superClass;
    public final String[] interfaces;
    public final String[] nestedClasses;
    public final FieldRepr[] fields;
    public final MethodRepr[] methods;
    public final String signature;

    public ClassRepr(final String fn, final String n, final String sig, final String sup, final String[] i, final String[] ns, final FieldRepr[] f, final MethodRepr[] m) {
        fileName = fn;
        name = n;
        superClass = sup;
        interfaces = i;
        nestedClasses = ns;
        fields = f;
        methods = m;
        signature = sig;
    }

    public ClassRepr(final BufferedReader r) {
        fileName = RW.readString(r);
        name = RW.readString(r);

        final String s = RW.readString(r);

        signature = s.length() == 0 ? null : s;

        superClass = RW.readString(r);
        interfaces = RW.readMany(r, RW.myStringConstructor, new ArrayList<String>()).toArray(dummyStrings);
        nestedClasses = RW.readMany(r, RW.myStringConstructor, new ArrayList<String>()).toArray(dummyStrings);
        fields = RW.readMany(r, FieldRepr.constructor, new ArrayList<FieldRepr>()).toArray(dummyFields);
        methods = RW.readMany(r, MethodRepr.constructor, new ArrayList<MethodRepr>()).toArray(dummyMethods);
    }

    public static RW.Constructor<ClassRepr> constructor = new RW.Constructor<ClassRepr>() {
        public ClassRepr read(final BufferedReader r) {
            return new ClassRepr(r);
        }
    };

    public void write(final BufferedWriter w) {
        RW.writeln(w, fileName);
        RW.writeln(w, name);
        RW.writeln(w, signature);
        RW.writeln(w, superClass);
        RW.writeln(w, interfaces, RW.fromString);
        RW.writeln(w, nestedClasses, RW.fromString);
        RW.writeln(w, fields, RW.fromWritable);
        RW.writeln(w, methods, RW.fromWritable);
    }

    public int compareTo(Object o) {
        return 0;
    }
}
