package org.jetbrains.annotations;

import java.lang.annotation.*;

@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE_USE})
public @interface NotNull {
  String value() default "";
  Class<? extends Exception> exception() default Exception.class;
}