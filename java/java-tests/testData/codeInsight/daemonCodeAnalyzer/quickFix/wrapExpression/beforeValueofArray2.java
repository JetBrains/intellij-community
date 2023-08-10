// "Wrap using 'Arrays.toString()'" "true-preview"
public class Test {
  void ba() {
    fa(new Object<caret>[] {1,2,3});
  }

  void fa(String s){}
}
