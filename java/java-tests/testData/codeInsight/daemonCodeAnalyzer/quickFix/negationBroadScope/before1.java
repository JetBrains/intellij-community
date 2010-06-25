// "Change to '!(1 == 1)'" "true"
public class Foo {
    void task() {
        if (!<caret>1 ==   1) {}
    }
}
