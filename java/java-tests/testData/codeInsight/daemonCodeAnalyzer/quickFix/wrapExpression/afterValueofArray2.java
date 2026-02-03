import java.util.Arrays;

// "Wrap using 'Arrays.toString()'" "true-preview"
public class Test {
  void ba() {
    fa(Arrays.toString(new Object[] {1,2,3}));
  }

  void fa(String s){}
}
