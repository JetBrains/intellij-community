import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target({ElementType.LOCAL_VARIABLE})
@interface Anno {}

class Test {
    void test() {
        newMethod();
        @Anno String s;
        s = "external assign";
    }

    private void newMethod() {
        @Anno String s;
        s = "";
    }
}