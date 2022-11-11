// "Move 'x' into anonymous object" "true-preview"
class Test {
    void test(Object o) {
        int x = 42;

        switch (o) {
            case null -> System.out.println(0);
            case Integer i -> System.out.println(1);
            case String s when s.length() == x<caret>++ -> System.out.println(2);
            default -> System.out.println(123);
        }
    }
}
