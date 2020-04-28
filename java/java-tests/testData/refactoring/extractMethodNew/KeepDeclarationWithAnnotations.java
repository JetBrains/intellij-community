import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target({ElementType.LOCAL_VARIABLE})
@interface Anno {}

class Test {
    void test() {
        <selection>@Anno String s;
        s = "";</selection>
        s = "external assign";
    }
}