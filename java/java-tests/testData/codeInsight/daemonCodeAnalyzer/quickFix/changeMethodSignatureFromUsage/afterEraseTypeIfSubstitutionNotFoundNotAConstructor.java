// "Add 'Foo' as 1st parameter to method 'bar'" "true-preview"
 public class Bar {
     static void bar(Foo foo, String args) {

     }
 }

class Foo {
    void bar() {
      Bar.bar(this, "");
    }
}