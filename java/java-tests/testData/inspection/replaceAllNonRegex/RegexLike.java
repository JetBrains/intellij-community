public class RegexLike {
  void m(String s) {
    s = s.replaceAll(".+", "t");
    s = s.replaceAll("[a-z]", "t");
    s = s.replaceAll("\\d+", "t");
    s = s.replaceAll("[<error descr="Unclosed character class">"</error>, "t");
  }
}