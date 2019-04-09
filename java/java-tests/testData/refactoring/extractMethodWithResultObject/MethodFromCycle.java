import java.util.List;

public class MethodFromCycle {
  public MethodFromCycle(Object o) {
  }

  private Object reduce(List<List<String>> list, List<String> input) {
    for (List<String> element : list) {
      <selection>
      for (String some : element) {
        List<String> tag = input;
        List<String> oldValue = tag;
        return new MethodFromCycle(withParameters(oldValue, tag));
      }
      </selection>
    }
    return null;
  }

  public Object withParameters(Object s, Object s1) {
    return null;
  }
}