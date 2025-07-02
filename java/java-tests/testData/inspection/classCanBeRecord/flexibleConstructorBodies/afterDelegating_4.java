// "Convert to record class" "true-preview"
record Person(String name, int age) {
    static int staticVar = 42;

    static void staticMethod() {
    }

    Person(String name) {
        System.out.println("age not passed" + staticVar);
        staticMethod();
        this(name, 0);
    }
}
