// "Transform body to single exit-point form" "true"
class Test {
  String<caret> process(String s) {
    if (s == null) return null;
    s = s.trim();
    if (s.isEmpty()) return null;
    System.out.println(s);
    String result = s + s;
    return result;
  }
}