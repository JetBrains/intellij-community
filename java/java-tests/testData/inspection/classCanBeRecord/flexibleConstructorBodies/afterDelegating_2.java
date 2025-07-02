// "Convert to record class" "true-preview"
record Person(String name, int age) {

    Person(String name) {
        System.out.println("age not passed" + name);
        this(name, 0);
    }
}
