// "Make 'Bar.f' protected" "true"
public class Foo extends Bar{
    void foo() {
        new Runnable(){
          public void run() {
            f();
          }
        };
    }
}
class Bar {
    protected void f(){}
}
