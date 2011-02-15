package org.jetbrains.ether.dependencyView;

import org.jetbrains.ether.RW;
import org.objectweb.asm.Type;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 01.02.11
 * Time: 4:56
 * To change this template use File | Settings | File Templates.
 */
public class FieldRepr implements RW.Writable {
    public final StringCache.S name;
    public final int access;
    public final Object value;
    public final TypeRepr.AbstractType type;
    public final String signature;

    public void updateClassUsages (final Set<UsageRepr.Usage> s) {
        type.updateClassUsages(s);
    }

    public FieldRepr(final int a, final String n, final String d, final String s, final Object v) {
        name = StringCache.get (n);
        access = a;
        value = v;
        type = TypeRepr.getType (d);
        signature = s;
    }

    private static Object readTyped(final BufferedReader r, final String tag) {
        if (tag.equals("string")) {
            return RW.readEncodedString(r);
        }

        if (tag.equals("none")) {
            return null;
        }

        final String val = RW.readString(r);

        if (tag.equals("integer"))
            return Integer.parseInt(val);

        if (tag.equals("long"))
            return Long.parseLong(val);

        if (tag.equals("float"))
            return Float.parseFloat(val);

        if (tag.equals("double"))
            return Double.parseDouble(val);

        return null;
    }

    public FieldRepr(final BufferedReader r) {
        name = StringCache.get(RW.readString(r));
        access = RW.readInt(r);

        final String s = RW.readString(r);

        signature = s.length() == 0 ? null : s;

        type = TypeRepr.reader.read(r);

        final String t = RW.readString(r);

        value = readTyped(r, t);
    }

    public void write(final BufferedWriter w) {
        RW.writeln(w, name.value);
        RW.writeln(w, Integer.toString(access));
        RW.writeln(w, signature);

        type.write(w);

        if (value instanceof String) {
            RW.writeln(w, "string");
            RW.writeEncodedString(w, (String) value);
        } else if (value instanceof Integer) {
            RW.writeln(w, "integer");
            RW.writeln(w, value.toString());
        } else if (value instanceof Long) {
            RW.writeln(w, "long");
            RW.writeln(w, value.toString());
        } else if (value instanceof Float) {
            RW.writeln(w, "float");
            RW.writeln(w, value.toString());
        } else if (value instanceof Double) {
            RW.writeln(w, "double");
            RW.writeln(w, value.toString());
        } else {
            RW.writeln(w, "none");
        }
    }

    public static RW.Reader<FieldRepr> reader = new RW.Reader<FieldRepr>() {
        public FieldRepr read(final BufferedReader r) {
            return new FieldRepr(r);
        }
    };
}
