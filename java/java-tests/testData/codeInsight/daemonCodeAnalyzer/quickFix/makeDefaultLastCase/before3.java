// "Make 'default' the last case" "true-preview"
class X {
    void test(String s) {
        switch (s) {
            default -> System.out.println("default");
            case "blah blah blah" -> System.out.println("blah blah blah");
            case null<caret> -> System.out.println("null");
        }
    }
}
