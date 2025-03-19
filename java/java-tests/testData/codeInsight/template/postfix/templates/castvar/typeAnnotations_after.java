@Target(ElementType.TYPE_USE)
@interface N {}
class M {
  void m(Object o) {
    if (o instanceof @N String) {
        String s = (String) o;<caret>
    }
  }
}