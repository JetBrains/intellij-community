// "Convert to record class" "false"
class Person<caret> {
    final String name;
    final int age;

    Person(String name, int age) {
        this.name = name;
        this.age = age;
    }

    Person(Person person) {
        System.out.println(this); // javac error: "cannot reference this before supertype constructor has been called"
        this(person.name, person.age);
    }
}