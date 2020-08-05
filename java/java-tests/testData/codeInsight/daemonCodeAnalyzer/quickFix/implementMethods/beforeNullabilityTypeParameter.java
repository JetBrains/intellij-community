// "Implement methods" "true"
package org.jetbrains.annotations;

import java.lang.annotation.*;

abstract class Foo<T> {
    abstract public @Nullable T getSmth();
}

<caret>class FooImpl extends Foo<String> {

}

@Target({ElementType.METHOD, ElementType.TYPE_USE})
@interface Nullable {}
