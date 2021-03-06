// "Add 'Foo' as 1st parameter to constructor 'Bar'" "true"
 public class Bar {
     Bar(Foo foo, String args) {

     }
 }

class Foo {
    void bar() {
      Bar bar = new Bar(this, "");
    }
}