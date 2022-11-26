// "Create parameter 'flag'" "true"
class Foo {
    void test(Object obj) {
        switch (obj) {
            case String s when flag<caret> -> System.out.println(1);
            default -> System.out.println(2);
        }
    }
}