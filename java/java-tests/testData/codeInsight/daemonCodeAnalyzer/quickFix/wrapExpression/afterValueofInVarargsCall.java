// "Wrap using 'Long.valueOf()'" "true"
public class Test {
  void ba(Long l) {
    fa(l, Long.valueOf("42"));
  }

  void fa(Long... l){}
}
