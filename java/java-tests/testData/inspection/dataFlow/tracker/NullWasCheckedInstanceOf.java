/*
Value is always false (s == null)
  The 'instanceof' check implies non-nullity (s instanceof String)
 */
class Test {
  void test(Object s) {
    if (s instanceof String) {
      if (<selection>s == null</selection>){

      }
    }
  }
}