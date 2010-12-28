// "Change signature of 'bar(String)' to 'bar(Foo<TypeParamName>, String)'" "true"
 public class Bar {
     static void bar(String args) {

     }
 }

class Foo<TypeParamName> {
    void bar() {
      Bar.bar(th<caret>is, "");
    }
}