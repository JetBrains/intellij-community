// "Import static constant 'java.lang.annotation.ElementType.FIELD'" "false"

interface FieldOfAnInterface {
  int FIELD = 1;

  static void main() {
    System.out.println(FIELD);
  }
}
interface FieldOfAnInterface2 {
  static boolean FIELD = true;
}
class X999 implements FieldOfAnInterface, FieldOfAnInterface2 {

  void f() {
    System.out.println(FIELD<caret>); // <- Error
  }

  static void main() {
    X999 x = new X999();
    x.f();
  }
}