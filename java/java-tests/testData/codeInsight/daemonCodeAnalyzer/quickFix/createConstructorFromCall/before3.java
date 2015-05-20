// "Create constructor" "true"
class Outer {
    public Outer(String s) {
    }

    void method(Outer other) {
        other.new <caret>CreateConstructorFromUsage("parameter"); // invoke "Create constructor" quick fix here
    }

    class CreateConstructorFromUsage {
    }
}