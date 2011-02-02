package org.jetbrains.ether.dependencyView;

import org.jetbrains.ether.RW;
import org.objectweb.asm.Type;

import java.io.BufferedReader;
import java.io.BufferedWriter;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 01.02.11
 * Time: 11:24
 * To change this template use File | Settings | File Templates.
 */

public class Usage implements RW.Writable {
    public final String name;
    public final String owner;
    //public final Type type;

    private final String descr;
    private final String kind;

    private final String representer;

    private Usage(final String k, final String n, final String o, final String d) {
        kind = k;
        name = n;
        owner = o;
        descr = d;

        representer = k + n + o + d;
        // type = Type.getType(descr);
    }

    public static Usage createFieldUsage(final String name, final String owner, final String descr) {
        return new Usage("fieldUsage", name, owner, descr);
    }

    public static Usage createMethodUsage(final String name, final String owner, final String descr) {
        return new Usage("methodUsage", name, owner, descr);
    }

    public boolean isFieldUsage() {
        return kind.equals("fieldUsage");
    }

    public Usage(final BufferedReader r) {
        kind = RW.readString(r);
        name = RW.readString(r);
        owner = RW.readString(r);
        descr = RW.readString(r);

        representer = kind + name + owner + descr;
        //type = Type.getType(descr);
    }

    public void write(final BufferedWriter w) {
        RW.writeln(w, kind);
        RW.writeln(w, name);
        RW.writeln(w, owner);
        RW.writeln(w, descr);
    }

    public static RW.Constructor<Usage> constructor = new RW.Constructor<Usage>() {
        public Usage read(final BufferedReader r) {
            return new Usage(r);
        }
    };

    public int compareTo(Object o) {
        return 0;
    }

    public int hashCode() {
        return representer.hashCode();
    }

    public boolean equals(Object obj) {
        if (obj instanceof Usage) {
            return ((Usage) obj).representer.equals(representer);
        }

        return false;
    }
}
