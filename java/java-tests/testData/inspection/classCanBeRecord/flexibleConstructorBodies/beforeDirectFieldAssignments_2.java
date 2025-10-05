// "Convert to record class" "false"
class Person<caret> {
    final String name;
    final int age;

    Person(String name, int age) {
        this.name = name;
        this.age = age;
    }

    Person(String name) {
        this.name = name;
        System.out.println("age not passed"); // cannot convert to delegating constructor call while preserving semantics
        this.age = 0;
    }
}
