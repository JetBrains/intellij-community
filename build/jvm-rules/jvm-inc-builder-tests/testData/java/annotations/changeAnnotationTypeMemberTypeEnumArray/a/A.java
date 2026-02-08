public @interface A {
  Val[] value() default {Val.V1, Val.V2, Val.V3};
}