// "Convert to record class" "true-preview"
record Person(String name, int age) {
    Person(int age, String name) {
        this(name, age);
    }

}
