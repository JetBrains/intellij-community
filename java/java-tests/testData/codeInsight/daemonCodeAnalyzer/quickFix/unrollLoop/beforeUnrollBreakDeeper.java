// "Unroll loop" "false"
class Test {
  void test(String s1, String s2, String s3) {
    fo<caret>r(String s : new String[] {s1, s2, s3}) {
      if (!s.isEmpty()) {
        if(s.length() > 5) break;
      }
      System.out.println("Long string: "+s);
      if(s.length() > 20) break;
      System.out.println("Very long string: "+s);
    }
  }

  void foo(boolean b) {}
}