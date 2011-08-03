// "Add Foo<TypeParamName> as 1 parameter to method Bar" "true"
 public class Bar {
     Bar(Foo<TypeParamName> typeParamNameFoo, String args) {

     }
 }

class Foo<TypeParamName> {
    void bar() {
      Bar bar = new Bar(this, "");
    }
}