// "Wrap using 'Arrays.toString()'" "true-preview"
public class Test {
  void ba() {
    fa(new int<caret>[10]);
  }

  void fa(String s){}
}
