// "Wrap using 'Long.valueOf()'" "true-preview"
public class Test {
  void ba(Long l) {
    fa(l, Long.valueOf("42"));
  }

  void fa(Long... l){}
}
