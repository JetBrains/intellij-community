/*
Value is always false (s1 == null)
  's1' was assigned (s.trim())
    Method 'trim' is externally annotated as 'non-null' (trim)
 */
class Test {
  void test(String s) {
    String s1 = s.trim();
    if (<selection>s1 == null</selection>) {
      
    }
  }
}