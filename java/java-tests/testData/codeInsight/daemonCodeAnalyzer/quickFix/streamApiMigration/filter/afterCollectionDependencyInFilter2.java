// "Collapse loop with stream 'forEach()'" "true-preview"
import java.util.ArrayList;
import java.util.List;

abstract class Sample implements List<String> {
  void main() {
      this.stream().filter(tag -> !foo(this)).forEach(tag -> add(tag.trim()));
  }
  
  static boolean foo(List<String> a){ return false;}
}
