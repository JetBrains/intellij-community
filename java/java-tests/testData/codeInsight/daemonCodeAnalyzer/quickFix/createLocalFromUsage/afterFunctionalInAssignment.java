// "Create local variable 'foo'" "true-preview"
class Foo {
    {
        A foo = (s, s1) -> {
        };
    }
}
@FunctionalInterface
interface A {
    void m(String s, String s1);
}