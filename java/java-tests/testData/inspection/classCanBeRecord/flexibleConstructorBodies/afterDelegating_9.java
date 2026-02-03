// "Convert to record class" "true-preview"
record Person(String name, int age) {

    Person(Person person) {
        System.out.println(person.hashCode());
        this(person.name, person.age);
    }
}
