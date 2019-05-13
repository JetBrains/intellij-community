// "Replace with forEach" "true"
import java.util.ArrayList;
import java.util.List;

class Sample extends ArrayList<String> {
  void main() {
      this.stream().filter(tag -> !super.contains(tag)).forEach(tag -> add(tag.trim()));
  }
  
  static boolean foo(List<String> a){ return false;}
}
