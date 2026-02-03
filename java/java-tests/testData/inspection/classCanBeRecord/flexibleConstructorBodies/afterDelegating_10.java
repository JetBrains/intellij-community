// "Convert to record class" "true-preview"
record Person(String name, int age) {

    Person(Person person) {
        new Object() {{
            hashCode();
        }};
        this(person.name, person.age);
    }
}
