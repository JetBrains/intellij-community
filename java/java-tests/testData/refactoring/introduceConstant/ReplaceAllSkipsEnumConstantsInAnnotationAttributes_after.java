import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

class MyClass {
    public static final ElementType xxx = ElementType.FIELD;

    @Target(value = {ElementType.FIELD})
    public @interface MyAnnotation {}

    ElementType type = xxx;
    ElementType another = xxx;
}
