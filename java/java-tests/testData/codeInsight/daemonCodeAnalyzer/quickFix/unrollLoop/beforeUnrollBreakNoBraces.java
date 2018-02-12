// "Unroll loop" "true"
class Test {
  void test(String s1, String s2, String s3) {
    fo<caret>r(String s : new String[] {s1, s2, s3})
      if((i+=s.length()) > 10) break;
    System.out.println(i);
  }

  void foo(boolean b) {}
}