// "Create constructor" "true"
class Outer {
    public Outer(String s) {
    }

    void method(Outer other) {
        other.new CreateConstructorFromUsage("parameter"); // invoke "Create constructor" quick fix here
    }

    class CreateConstructorFromUsage {
        public CreateConstructorFromUsage(String parameter) {
            <selection></selection>
        }
    }
}