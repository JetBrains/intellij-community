// "Change 2nd parameter of method 'f' from 'Integer' to 'String'" "true-preview"
import java.lang.annotation.*;

class A {
    void f(@Anno @Anno2 @Anno3 String s, @Anno Integer i) {}
    public void foo() {
        <caret>f("s", "x");
    }

    @Target({ElementType.PARAMETER, ElementType.TYPE_USE})
    @interface Anno {}

    @Target({ElementType.TYPE_USE})
    @interface Anno2 {}

    @Target({ElementType.PARAMETER})
    @interface Anno3 {}
}