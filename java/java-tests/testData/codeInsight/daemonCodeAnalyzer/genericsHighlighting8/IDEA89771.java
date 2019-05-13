public @interface MyAnnotation {
    Class<? extends Enum<?>> enumClass() default <error descr="Incompatible types. Found: 'java.lang.Class<java.lang.Enum>', required: 'java.lang.Class<? extends java.lang.Enum<?>>'">Enum.class</error>;
}
