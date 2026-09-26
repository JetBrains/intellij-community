package org.example;

import org.jspecify.annotations.Nullable;

public class FlowMethodTests {
    <T extends @Nullable Object> T firstNonNull(@Nullable T a, T b) {
        if (a != null) return a;
        return b;
    }

    <T extends @Nullable Object> T brokenFirstNonNull(@Nullable T a, T b) {
        return <warning descr="Expression 'a' might evaluate to null but is returned from a method whose type-variable return type may be instantiated as non-null">a</warning>;
    }

    <T extends @Nullable Object> T wierdNull(@Nullable T a, T b) {
        if (b == null) return null;
        return b;
    }

    <T extends @Nullable Object> T ensure(@Nullable T a, T def) {
        if (a == null) a = def;
        return a;
    }
}
