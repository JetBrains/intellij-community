/*
Value is always false (s1 == null; line#9)
  's1' was assigned (=; line#8)
    Method 'trim' is externally annotated as 'non-null' (trim; line#8)
 */
class Test {
  void test(String s) {
    String s1 = s.trim();
    if (<selection>s1 == null</selection>) {
      
    }
  }
}