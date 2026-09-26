// "Convert to record class" "true"

record User(String name, int age, String tag) {
    User(String name, int age) {
        this(name, age, null);
    }

}
