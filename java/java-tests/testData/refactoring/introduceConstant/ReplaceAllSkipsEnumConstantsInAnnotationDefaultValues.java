import java.lang.annotation.ElementType;

class MyClass {
    public @interface MyAnnotation {
        ElementType value() default ElementType.FIELD;
        ElementType[] many() default {ElementType.FIELD};
    }

    ElementType type = ElementType.FI<caret>ELD;
    ElementType another = ElementType.FIELD;
}
