// "Make 'default' the last case" "true-preview"
class X {
    void test(String s) {
        switch (s) {
            case "blah blah blah" -> System.out.println("blah blah blah");
            case null -> System.out.println("null");
            default -> System.out.println("default");
        }
    }
}
