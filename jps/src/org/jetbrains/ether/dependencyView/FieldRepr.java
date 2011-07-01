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
public class FieldRepr extends ProtoMember {

    public final Object value;

    public Difference difference(final Proto past) {
        int diff = super.difference(past).base();

        final FieldRepr field = (FieldRepr) past;

        switch ((value == null ? 0 : 1) + (field.value == null ? 0 : 2)) {
            case 3:
                if (!value.equals(field.value)) {
                    diff |= Difference.VALUE;
                }
                break;

            case 2:
            case 1:
                diff |= Difference.VALUE;
                break;

            case 0:
                break;
        }

        return Difference.createBase(diff);
    }

    public void updateClassUsages(final Set<UsageRepr.Usage> s) {
        type.updateClassUsages(s);
    }

    public FieldRepr(final int a, final String n, final String d, final String s, final Object v) {
        super(a, s, StringCache.get(n), TypeRepr.getType(d));
        value = v;
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
        super(r);
        value = readTyped(r, RW.readString(r));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final FieldRepr fieldRepr = (FieldRepr) o;

        return name.equals(fieldRepr.name) && type.equals(fieldRepr.type);
    }

    @Override
    public int hashCode() {
        return 31 * name.hashCode() + type.hashCode();
    }

    public void write(final BufferedWriter w) {
        super.write(w);

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

    public UsageRepr.Usage createUsage (final StringCache.S owner) {
        return UsageRepr.createFieldUsage(name.value, owner.value, type.getDescr());
    }
}
