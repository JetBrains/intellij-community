// "Change to '!(this instanceof Object)'" "true"
public class Foo {
    void task() {
        if (<caret>!(this instanceof Object)) {}
    }
}
