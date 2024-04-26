@Target(ElementType.TYPE_USE)
@interface N {}
class A {
  void m(@N String par) {
    par.field<caret>
  }
}