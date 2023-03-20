// "Split values of 'switch' branch" "true-preview"
class X {
    void test(Object obj) {
        switch (obj) {
            case Integer i -> System.out.println("Integer");
            case null, default<caret> -> System.out.println("null or default");
            case String s when s.isEmpty() -> System.out.println("empty String");
        }
    }
}
