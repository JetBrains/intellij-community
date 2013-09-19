import java.lang.annotation.Annotation;

public @interface Foo {
    String id();
}

class Bar implements Foo {
    public String id() {
        return null;
    }

    public Class<? extends Annotation> annotationType() {
        return null;
    }
}
