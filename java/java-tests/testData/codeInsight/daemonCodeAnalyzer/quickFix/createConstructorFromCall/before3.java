// "Create Constructor" "true"
class Outer {
    public Outer(String s) {
    }

    void method(Outer other) {
        other.new <caret>CreateConstructorFromUsage("parameter"); // invoke "Create Constructor" quick fix here
    }

    class CreateConstructorFromUsage {
    }
}