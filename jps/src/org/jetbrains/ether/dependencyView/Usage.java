package org.jetbrains.ether.dependencyView;

import org.jetbrains.ether.RW;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 01.02.11
 * Time: 11:24
 * To change this template use File | Settings | File Templates.
 */

public class Usage implements RW.Writable {
    private final static Map<Usage, Usage> myCache = new HashMap<Usage, Usage>();

    private static Usage cached(final Usage u) {
        final Usage v = myCache.get(u);

        if (v == null) {
            myCache.put(u, u);
            return u;
        }

        return v;
    }

    public final String name;
    public final String owner;
    private final String descr;
    private final String kind;

    private Usage(final String k, final String n, final String o, final String d) {
        kind = k;
        name = n;
        owner = o;
        descr = d;
    }

    public static Usage createFieldUsage(final String name, final String owner, final String descr) {
        return cached(new Usage("fieldUsage", name, owner, descr));
    }

    public static Usage createMethodUsage(final String name, final String owner, final String descr) {
        return cached(new Usage("methodUsage", name, owner, descr));
    }

    public boolean isFieldUsage() {
        return kind.equals("fieldUsage");
    }

    public Usage(final BufferedReader r) {
        kind = RW.readString(r);
        name = RW.readString(r);
        owner = RW.readString(r);
        descr = RW.readString(r);
    }

    public void write(final BufferedWriter w) {
        RW.writeln(w, kind);
        RW.writeln(w, name);
        RW.writeln(w, owner);
        RW.writeln(w, descr);
    }

    public static RW.Reader<Usage> reader = new RW.Reader<Usage>() {
        public Usage read(final BufferedReader r) {
            return cached (new Usage(r));
        }
    };

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Usage usage = (Usage) o;

        if (!descr.equals(usage.descr)) return false;
        if (!kind.equals(usage.kind)) return false;
        if (!name.equals(usage.name)) return false;
        if (!owner.equals(usage.owner)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + owner.hashCode();
        result = 31 * result + descr.hashCode();
        result = 31 * result + kind.hashCode();
        return result;
    }
}
