// "Implement methods" "true"
package org.jetbrains.annotations;

import java.lang.annotation.*;

abstract class Test {
    abstract void foo(@NotNull String @NotNull[] data);
    
    abstract void foo2(@NotNull String @NotNull ... data);
}

<caret>class TImple extends Test {}

@Target({ElementType.PARAMETER, ElementType.TYPE_USE})
@interface NotNull {}
