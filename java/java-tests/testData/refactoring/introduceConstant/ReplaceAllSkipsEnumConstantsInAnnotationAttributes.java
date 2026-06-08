import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

class MyClass {
    @Target(value = {ElementType.FIELD})
    public @interface MyAnnotation {}

    ElementType type = ElementType.FI<caret>ELD;
    ElementType another = ElementType.FIELD;
}
