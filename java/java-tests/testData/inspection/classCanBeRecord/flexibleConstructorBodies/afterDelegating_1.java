// "Convert to record class" "true-preview"
record Person(String name, int age) {

    Person(String name) {
        System.out.println("age not passed");
        this(name, 0);
    }
}
