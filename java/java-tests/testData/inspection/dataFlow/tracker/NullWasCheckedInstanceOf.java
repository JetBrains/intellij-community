/*
Value is always false (s == null; line#8)
  The 'instanceof' check implies non-nullity (s instanceof String; line#7)
 */
class Test {
  void test(Object s) {
    if (s instanceof String) {
      if (<selection>s == null</selection>){

      }
    }
  }
}