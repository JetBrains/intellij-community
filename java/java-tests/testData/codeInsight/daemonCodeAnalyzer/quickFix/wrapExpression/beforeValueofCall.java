// "Wrap using 'Long.valueOf()'" "true-preview"
public class Test {
  void ba() {
    fa("4<caret>2");
  }

  void fa(Long l){}
}
