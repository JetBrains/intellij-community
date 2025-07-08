// "Convert to record class" "false"
class Person<caret> {
    final String name;
    int age = 0;

    Person(String name, int age) {
        this.name = name;
        this.age = age;
    }

    Person(String name) {
        this(name, 0 + age); // javac error: "cannot reference age before supertype constructor has been called"
    }
}
