package org.jetbrains.ether.dependencyView;

import org.jetbrains.ether.RW;

import java.io.BufferedReader;
import java.io.BufferedWriter;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 07.03.11
 * Time: 19:54
 * To change this template use File | Settings | File Templates.
 */
public abstract class ProtoMember extends Proto {
    public final TypeRepr.AbstractType type;

    protected ProtoMember (final int access, final String signature, final StringCache.S name, final TypeRepr.AbstractType t) {
        super (access, signature, name);
        this.type = t;
    }

    protected ProtoMember (final BufferedReader r) {
        super(r);
        type = TypeRepr.reader.read(r);
    }

    public void write (final BufferedWriter w) {
        super.write(w);
        type.write(w);
    }

    public Difference difference (final Proto past) {
        int diff = super.difference(past).base();

        if (!((ProtoMember) past).type.equals(type)) {
            diff |= Difference.TYPE;
        }

        return Difference.createBase(diff);
    }
}
