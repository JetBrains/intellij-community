// "Convert to record class" "false"
class Person<caret> {
    final String name;
    final int age;

    Person(String name, int age) {
        this.name = name;
        this.age = age;
    }

    Person(String name) {
        System.out.println("age not passed" + gibberish); // javac error: "cannot find symbol"
        this(name, 0);
    }
}
