// "Implement methods" "true"
package org.jetbrains.annotations;

import java.lang.annotation.*;

abstract class Test {
    abstract void foo(@NotNull String @NotNull[] data);
    
    abstract void foo2(@NotNull String @NotNull ... data);
}

class TImple extends Test {
    @Override
    void foo(@NotNull String @NotNull [] data) {
        
    }

    @Override
    void foo2(@NotNull String @NotNull ... data) {

    }
}

@Target({ElementType.PARAMETER, ElementType.TYPE_USE})
@interface NotNull {}
