public class Foo {
    public Foo(String ... strs) {}

    public void test1(Foo o, String... foo) {}
    void bar() {
        String[] strs = {"", ""};
        test1(new Foo(strs));
    }
}