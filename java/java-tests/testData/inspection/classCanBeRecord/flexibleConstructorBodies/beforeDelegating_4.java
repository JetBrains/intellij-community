// "Convert to record class" "true-preview"
class Person<caret> {
    final String name;
    final int age;
    static int staticVar = 42;

    static void staticMethod() {
    }

    Person(String name, int age) {
        this.name = name;
        this.age = age;
    }

    Person(String name) {
        System.out.println("age not passed" + staticVar);
        staticMethod();
        this(name, 0);
    }
}
