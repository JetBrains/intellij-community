// "Convert to record class" "false"
class Person<caret> {
    final String myName;
    final int age;

    Person(String name, int age) {
        this.myName = name;
        this.age = age;
    }

    Person(String name) {
        System.out.println("age not passed" + myName); // javac error: "cannot reference myName before supertype constructor has been called"
        this(name, 0);
    }
}
