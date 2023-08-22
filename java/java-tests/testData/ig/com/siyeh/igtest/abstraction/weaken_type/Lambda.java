package com.siyeh.igtest.abstraction.weaken_type;

import java.util.function.Supplier;

public interface Lambda {

    int count();

    static Lambda newWeaken(int value) {
        return new Lambda() {
            @Override
            public int count() {
                return value;
            }
        };
    }

    static Supplier<Lambda> newSupplier() {
        return () -> {
            return Lambda.newWeaken(0);
        };
    }
}