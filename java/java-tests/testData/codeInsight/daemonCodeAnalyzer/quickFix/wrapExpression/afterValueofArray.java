import java.util.Arrays;

// "Wrap using 'Arrays.toString()'" "true-preview"
public class Test {
  void ba() {
    fa(Arrays.toString(new int[10]));
  }

  void fa(String s){}
}
