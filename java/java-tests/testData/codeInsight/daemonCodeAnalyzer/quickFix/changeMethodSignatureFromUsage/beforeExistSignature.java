// "Add 'int' as 1st parameter to method 'f'" "false"
public class Foo {
    void foo(Bar f) {
        f.f<caret>(2);
    }
}
class Bar {
    private void f(int i){}
    public void f(String s){}


}
