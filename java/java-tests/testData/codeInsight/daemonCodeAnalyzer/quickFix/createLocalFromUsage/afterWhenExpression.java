// "Create local variable 'flag'" "true-preview"
class Foo {
    void test(Object obj) {
        boolean flag;
        switch (obj) {
            case String s when flag -> System.out.println(1);
            default -> System.out.println(2);
        }
    }
}