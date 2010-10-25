// "Make 'Bar.f' protected" "true"
public class Foo extends Bar{
    void foo() {
        new Runnable(){
          public void run() {
            f<caret>();
          }
        };
    }
}
class Bar {
    private void f(){}
}
