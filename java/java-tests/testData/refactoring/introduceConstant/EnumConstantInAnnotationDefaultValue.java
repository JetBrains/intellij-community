import java.lang.annotation.ElementType;

class MyClass {
    public @interface MyAnnotation {
        ElementType value() default ElementType.FI<caret>ELD;
    }
}
