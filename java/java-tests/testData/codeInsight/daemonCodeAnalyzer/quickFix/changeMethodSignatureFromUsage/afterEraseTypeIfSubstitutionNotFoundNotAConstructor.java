// "Change signature of 'bar(String)' to 'bar(Foo<TypeParamName>, String)'" "true"
 public class Bar {
     static void bar(Foo<TypeParamName> typeParamNameFoo, String args) {

     }
 }

class Foo<TypeParamName> {
    void bar() {
      Bar.bar(this, "");
    }
}