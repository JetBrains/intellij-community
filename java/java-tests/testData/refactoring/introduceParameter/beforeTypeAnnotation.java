import java.lang.annotation.*;

@Target(value = ElementType.TYPE_USE)
public @interface TA { }

class Test {
    void m() {
        @TA String <caret>v = "smth";
        System.out.println(v);
    }

    void use() {
        m();
    }
}