// "Wrap using 'Long.parseLong()'" "true-preview"
public class Test {
  void ba(long l) {
    fa(l, Long.parseLong("42"));
  }

  void fa(long... l){}
}
