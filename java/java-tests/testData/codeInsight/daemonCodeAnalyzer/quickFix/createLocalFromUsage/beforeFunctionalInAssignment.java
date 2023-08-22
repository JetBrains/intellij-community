// "Create local variable 'foo'" "true-preview"
class Foo {
    {
        f<caret>oo = (s, s1) -> {};
    }
}
@FunctionalInterface
interface A {
    void m(String s, String s1);
}