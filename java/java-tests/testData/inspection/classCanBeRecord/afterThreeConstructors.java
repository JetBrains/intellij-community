// "Convert to record class" "true-preview"

record Person(String name, int age) {

    Person(String name) {
        this(name, 42);
    }

    Person(int age) {
        this("Unknown", age);
    }
}
