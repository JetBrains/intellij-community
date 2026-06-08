import java.lang.annotation.ElementType;

class MyClass {
    public static final ElementType xxx = ElementType.FIELD;

    public @interface MyAnnotation {
        ElementType value() default ElementType.FIELD;
        ElementType[] many() default {ElementType.FIELD};
    }

    ElementType type = xxx;
    ElementType another = xxx;
}
