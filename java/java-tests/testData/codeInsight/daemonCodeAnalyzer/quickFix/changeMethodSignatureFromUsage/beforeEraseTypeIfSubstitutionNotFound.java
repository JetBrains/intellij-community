// "Change signature of 'Bar(String)' to 'Bar(Foo<TypeParamName>, String)'" "true"
 public class Bar {
     Bar(String args) {

     }
 }

class Foo<TypeParamName> {
    void bar() {
      Bar bar = new Bar(th<caret>is, "");
    }
}