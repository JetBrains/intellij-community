@Target(ElementType.TYPE_USE)
@interface N {}
class M {
  void m(Oject o) {
    if (o instanceof @N String) {
        String s = (String) o;<caret>
    }
  }
}