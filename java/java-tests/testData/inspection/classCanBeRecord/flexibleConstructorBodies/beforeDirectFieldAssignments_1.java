// "Convert to record class" "true-preview"
class Person<caret> {
    final String name;
    final int age;

    Person(String name, int age) {
        this.name = name;
        this.age = age;
    }

    Person(String name) {
        System.out.println("age not passed");
        this.name = name;
        this.age = 0;
    }
}
