// "Convert to record class" "false"
class Person<caret> {
    final String name;
    final int age;

    Person(String name, int age) {
        this.name = name;
        this.age = age;
    }

    Person(Person person) {
        person = this; //
        System.out.println(person.name);
        this(person.name, person.age);
    }
}
