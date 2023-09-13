// "Make 'default' the last case" "true-preview"
class X {
    void test(Object obj) {
        switch (obj) {
            case null, default -> System.out.println("null or default");
            case Integer i -> System.out.println("Integer");
            case <caret>String s when s.isEmpty() -> System.out.println("empty String");
        }
    }
}
