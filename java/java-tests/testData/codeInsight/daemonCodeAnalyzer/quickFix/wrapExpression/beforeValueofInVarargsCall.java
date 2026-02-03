// "Wrap using 'Long.valueOf()'" "true-preview"
public class Test {
  void ba(Long l) {
    fa(l, "4<caret>2");
  }

  void fa(Long... l){}
}
