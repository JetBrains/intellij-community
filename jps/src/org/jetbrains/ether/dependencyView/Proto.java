package org.jetbrains.ether.dependencyView;

import groovyjarjarasm.asm.Opcodes;
import org.jetbrains.ether.RW;

import java.io.BufferedReader;
import java.io.BufferedWriter;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 01.03.11
 * Time: 17:57
 * To change this template use File | Settings | File Templates.
 */
public abstract class Proto implements RW.Writable {
    public final int access;
    public final StringCache.S signature;
    public final StringCache.S name;

    protected Proto(final int access, final String signature, final StringCache.S name) {
        this.access = access;
        this.signature = StringCache.get(signature != null ? signature : "");
        this.name = name;
    }

    protected Proto (final BufferedReader r) {
        access = RW.readInt (r);
        signature = StringCache.get(RW.readString(r));
        name = StringCache.get(RW.readString(r));
    }

    public void write (final BufferedWriter w) {
        RW.writeln(w, Integer.toString (access));
        RW.writeln(w, signature.value);
        RW.writeln(w, name.value);
    }

    public Difference difference (final Proto past) {
        int diff = Difference.NONE;

        if (past.access != access) {
            diff |= Difference.ACCESS;
        }

        if (! past.signature.equals(signature)) {
            diff |= Difference.SIGNATURE;
        }

        final int base = diff;

        return new Difference() {
            @Override
            public int base() {
                return base;
            }

            @Override
            public boolean no() {
                return base == Difference.NONE;
            }

            @Override
            public int addedModifiers() {
                return ~past.access & access;
            }

            @Override
            public int removedModifiers() {
                return ~access & past.access;
            }

            @Override
            public boolean packageLocalOn() {
                return
                        ((past.access & Opcodes.ACC_PRIVATE) != 0 ||
                                (past.access & Opcodes.ACC_PUBLIC) != 0 ||
                                (past.access & Opcodes.ACC_PROTECTED) != 0
                        )

                                &&

                                ((access & Opcodes.ACC_PRIVATE) == 0 &&
                                        (access & Opcodes.ACC_PROTECTED) == 0 &&
                                        (access & Opcodes.ACC_PUBLIC) == 0);
            }
        };
    }
}
