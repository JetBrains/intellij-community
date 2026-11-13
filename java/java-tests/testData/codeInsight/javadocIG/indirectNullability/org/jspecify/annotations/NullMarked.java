package org.jspecify.annotations;
import java.lang.annotation.*;
@Documented
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.MODULE})
public @interface NullMarked {}