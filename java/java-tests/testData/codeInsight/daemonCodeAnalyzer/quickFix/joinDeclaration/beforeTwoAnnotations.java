// "Join declaration and assignment" "GENERIC_ERROR_OR_WARNING"
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

class C {
    void foo() {
        @A @B int k, n, m;
        <caret>n = 1;
    }
    @Target(ElementType.LOCAL_VARIABLE) @interface A {}
    @Target(ElementType.LOCAL_VARIABLE) @interface B {}
}