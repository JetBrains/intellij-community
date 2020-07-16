// "Implement methods" "true"
package org.jetbrains.annotations;

import java.lang.annotation.*;

abstract class Foo<T> {
    abstract public @Nullable T getSmth();
}

class FooImpl extends Foo<String> {

    @Override
    public @Nullable String getSmth() {
        return null;
    }
}

@Target({ElementType.METHOD, ElementType.TYPE_USE})
@interface Nullable {}
