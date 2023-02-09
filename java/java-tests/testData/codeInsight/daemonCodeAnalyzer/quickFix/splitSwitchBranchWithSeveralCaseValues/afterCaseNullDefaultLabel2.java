// "Split values of 'switch' branch" "true-preview"
class X {
    void test(Object obj) {
        switch (obj) {
            case Integer i:
                System.out.println("Integer");
                break;
            case String s when s.isEmpty():
                System.out.println("empty String");
                break;
            case null:
                System.out.println("null or default");
                break;
            default:
                System.out.println("null or default");
                break;
        }
    }
}
