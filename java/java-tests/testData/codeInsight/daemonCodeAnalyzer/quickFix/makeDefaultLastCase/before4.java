// "Make 'default' the last case" "true-preview"
class X {
    void test(String s) {
        switch (s) {
            case null, default -> System.out.println("null, default");
            case "blah blah blah"<caret> -> System.out.println("blah blah blah");
        }
    }
}
