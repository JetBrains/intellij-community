// "Make 'default' the last case" "true-preview"
class X {
    void test(Object obj) {
        switch (obj) {
            case Integer i -> System.out.println("Integer");
            case String s when s.isEmpty() -> System.out.println("empty String");
            case null, default -> System.out.println("null or default");
        }
    }
}
