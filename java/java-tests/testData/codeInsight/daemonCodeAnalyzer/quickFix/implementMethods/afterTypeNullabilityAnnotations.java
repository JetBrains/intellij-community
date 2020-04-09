// "Implement methods" "true"
package org.jetbrains.annotations;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE_USE})
@interface Nullable{}

@Target({ElementType.METHOD, ElementType.TYPE_USE})
@interface NotNull{}

interface A {
    @Nullable Object @NotNull [] getNotNullArrayOfNullableObjects();
    @NotNull Object @Nullable [] getNullableArrayOfNotNullObjects();
}

class B implements A {
    @Override
    public @Nullable Object @NotNull [] getNotNullArrayOfNullableObjects() {
        return new Object[0];
    }

    @Override
    public @NotNull Object @Nullable [] getNullableArrayOfNotNullObjects() {
        return new Object[0];
    }
}