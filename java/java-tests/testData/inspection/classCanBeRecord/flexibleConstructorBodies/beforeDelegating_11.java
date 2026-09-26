// "Convert to record class" "false"
class Person<caret> {
    final String name;
    final int age;

	  Person(String name, int age) {
		    this.name = name;
		    this.age = age;
	  }

    Person(Person person) {
        new Other(hashCode()) {};
        this(person.name, person.age);
    }
}

class Other {
    Other(int x) {
    }
}