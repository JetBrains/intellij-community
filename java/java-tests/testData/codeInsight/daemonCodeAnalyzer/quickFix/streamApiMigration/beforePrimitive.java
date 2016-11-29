// "Replace with forEach" "true"
import java.util.List;

class Sample {
  public void some(List<String> from, List<Integer> to) {
    for (String key : fr<caret>om) {
      to.add(key.length());
    }
  }
}
