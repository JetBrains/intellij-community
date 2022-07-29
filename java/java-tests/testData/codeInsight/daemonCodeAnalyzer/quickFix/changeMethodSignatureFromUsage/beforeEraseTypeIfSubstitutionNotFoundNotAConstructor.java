// "Add 'Foo' as 1st parameter to method 'bar'" "true-preview"
 public class Bar {
     static void bar(String args) {

     }
 }

class Foo {
    void bar() {
      Bar.bar(th<caret>is, "");
    }
}