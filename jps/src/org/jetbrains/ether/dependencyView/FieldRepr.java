package org.jetbrains.ether.dependencyView;

import org.jetbrains.ether.RW;
import org.objectweb.asm.Type;

import java.io.BufferedReader;
import java.io.BufferedWriter;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 01.02.11
 * Time: 4:56
 * To change this template use File | Settings | File Templates.
 */
public class FieldRepr implements RW.Writable {
    public final String name;
    //public final Type type;
    public final String signature;

    private final String descr;

    public FieldRepr (final String n, final String d, final String s) {
        name = n;
        //type = Type.getType (d);
        signature = s;
        descr = d;
    }

    public FieldRepr (final BufferedReader r) {
        name = RW.readString(r);
        descr = RW.readString(r);

        final String s = RW.readString(r);

        signature = s.length() == 0 ? null : s;

        //type = Type.getType(descr);
    }

    public void write(final BufferedWriter w) {
        RW.writeln (w, name);
        RW.writeln (w, descr);
        RW.writeln (w, signature);
    }

    public static RW.Reader<FieldRepr> reader = new RW.Reader<FieldRepr>() {
        public FieldRepr read(final BufferedReader r) {
            return new FieldRepr (r);
        }
    };
}
