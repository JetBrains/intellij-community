// "Replace with 'clone()'" "true"
import java.util.Arrays;

class Test {
  String[] arr;

  String[] get() {
    return arr.clone();
  }
}