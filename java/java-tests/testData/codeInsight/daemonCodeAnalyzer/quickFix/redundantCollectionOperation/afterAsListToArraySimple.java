// "Replace with 'clone()'" "true-preview"
import java.util.Arrays;

class Test {
  String[] arr;

  String[] get() {
    return arr.clone();
  }
}