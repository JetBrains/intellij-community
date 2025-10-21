// "Convert to record class" "false"
class Person<caret> {
    final String name;
    final int age;

    Person(String name, int age) {
        this.name = name;
        this.age = age;
    }

    Person(String name) {
        System.out.println("age not passed" + this.name); // javac error: "cannot reference 'this' before superclass constructor is called"
        this(name, 0);
    }
}
