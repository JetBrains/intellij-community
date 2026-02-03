import java.lang.annotation.*;

@Target(value = ElementType.TYPE_USE)
public @interface TA { }

class Test {
    void m(@TA String anObject) {
        System.out.println(anObject);
    }

    void use() {
        m("smth");
    }
}