// "Transform body to single exit-point form" "true-preview"
class Test {
  String process(String s) {
      String res = null;
      if (s != null) {
          s = s.trim();
          if (!s.isEmpty()) {
              System.out.println(s);
              String result = s + s;
              res = result;
          }
      }
      return res;
  }
}