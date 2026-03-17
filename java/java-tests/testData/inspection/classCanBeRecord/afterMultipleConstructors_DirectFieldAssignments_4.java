// "Convert to record class" "true"

record User(String name, int age, String tag) {
    private static final int DEFAULT_AGE = 18;

    User(String name, int age) {
        this(name, DEFAULT_AGE, null);
    }

}
