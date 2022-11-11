// "Wrap using 'Long.parseLong()'" "true-preview"
public class Test {
  void ba(long l) {
    fa(l, "4<caret>2");
  }

  void fa(long... l){}
}
