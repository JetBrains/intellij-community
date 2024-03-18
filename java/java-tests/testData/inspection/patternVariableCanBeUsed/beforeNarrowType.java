// "Replace 'cs' with pattern variable" "true"
public class ThisClass {
  void test(Object obj) {
    if (obj instanceof String) {
      CharSequence c<caret>s = (CharSequence) obj;
      use(cs);
    }
  }

  void use(CharSequence cs) {}
}