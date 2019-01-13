// "Add 'Foo' as 1st parameter to method 'Bar'" "true"
 public class Bar {
     Bar(Foo foo, String args) {

     }
 }

class Foo {
    void bar() {
      Bar bar = new Bar(this, "");
    }
}