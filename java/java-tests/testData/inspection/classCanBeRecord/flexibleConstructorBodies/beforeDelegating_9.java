// "Convert to record class" "true-preview"
class Person<caret> {
    final String name;
    final int age;

    Person(String name, int age) {
        this.name = name;
        this.age = age;
    }

	  Person(Person person) {
		    System.out.println(person.hashCode());
		    this(person.name, person.age);
    }
}
