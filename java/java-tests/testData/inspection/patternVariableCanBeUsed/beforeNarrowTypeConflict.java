// "Replace 'cs' with pattern variable" "false"
public class ThisClass {
  void test(Object obj) {
    if (obj instanceof String) {
      CharSequence c<caret>s = (CharSequence) obj;
      use(cs);
    }
  }

  void use(CharSequence cs) {}
  
  void use(String s) {}
}