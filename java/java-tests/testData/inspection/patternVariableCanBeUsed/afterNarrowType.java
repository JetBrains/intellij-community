// "Replace 'cs' with pattern variable" "true"
public class ThisClass {
  void test(Object obj) {
    if (obj instanceof String cs) {
        use(cs);
    }
  }

  void use(CharSequence cs) {}
}