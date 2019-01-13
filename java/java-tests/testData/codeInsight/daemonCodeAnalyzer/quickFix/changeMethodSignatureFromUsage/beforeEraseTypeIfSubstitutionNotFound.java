// "Add 'Foo' as 1st parameter to method 'Bar'" "true"
 public class Bar {
     Bar(String args) {

     }
 }

class Foo {
    void bar() {
      Bar bar = new Bar(th<caret>is, "");
    }
}