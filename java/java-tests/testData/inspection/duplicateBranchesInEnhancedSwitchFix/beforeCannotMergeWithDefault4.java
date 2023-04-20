// "Merge with the default 'switch' branch" "false"
class Test {
    void foo(String s) {
        switch (s) {
            case "blah blah blah" -> System.out.println(<caret>"hello");
            case null, default -> System.out.println("hello");
        }
    }
}
