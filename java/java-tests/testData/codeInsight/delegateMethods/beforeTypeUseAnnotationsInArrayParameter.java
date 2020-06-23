package org.jetbrains.annotations;

import java.lang.annotation.*;

@Target({ElementType.PARAMETER, ElementType.TYPE_USE})
@interface NotNull {}

class Test1 {
    void m(@NotNull String... strings) { }
}

class Test2 {
    private Test1 t;

    <caret>
}