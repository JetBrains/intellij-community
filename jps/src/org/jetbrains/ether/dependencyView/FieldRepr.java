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
    public void updateClassUsages(final String res, final Set<UsageRepr.Usage> s) {
        type.updateClassUsages(res, s);
    }

    public FieldRepr(final int a, final String n, final String d, final String s, final Object v) {
        super(a, s, StringCache.get(n), TypeRepr.getType(d), v);
    }

    public FieldRepr(final BufferedReader r) {
        super(r);
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

    public static RW.Reader<FieldRepr> reader = new RW.Reader<FieldRepr>() {
        public FieldRepr read(final BufferedReader r) {
            return new FieldRepr(r);
        }
    };

    public UsageRepr.Usage createUsage (final String res, final StringCache.S owner) {
        return UsageRepr.createFieldUsage(res, name.value, owner.value, type.getDescr());
    }
}
