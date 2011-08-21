// "Add 'Foo<TypeParamName>' as 1st parameter to method 'bar'" "true"
 public class Bar {
     static void bar(Foo<TypeParamName> typeParamNameFoo, String args) {

     }
 }

class Foo<TypeParamName> {
    void bar() {
      Bar.bar(this, "");
    }
}