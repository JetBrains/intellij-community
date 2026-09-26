// "Convert to record class" "true"
record Person(String name, int age) {

    Person(String myName, int myAge, String a) {
        this(myName, myAge);
    }
}
