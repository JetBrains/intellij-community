// "Create parameter 'flag'" "true-preview"
class Foo {
    void test(Object obj, boolean flag) {
        switch (obj) {
            case String s && flag -> System.out.println(1);
            default -> System.out.println(2);
        }
    }
}