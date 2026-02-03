public class Inheritance {}

class Base {
  void f<caret>oo(){
    System.out.println("");
  }
}

class Child extends Base {
   void bar() {
     System.out.println("");
   }
}