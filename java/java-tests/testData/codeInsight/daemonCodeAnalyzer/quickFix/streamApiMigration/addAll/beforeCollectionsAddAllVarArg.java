// "Replace with toArray" "true"
import java.util.*;

public class Test {
  Object[] test(List<String> list) {
    List<Object> result = new LinkedList<>();
    for(String str : lis<caret>t) {
      if(str != null) {
        Collections.addAll(result, str, str+str);
      }
    }
    result.sort(null);
    return result.toArray();
  }

  public static void main(String[] args) {
    System.out.println(Arrays.toString(new Test().test(Arrays.asList("a", "b", "ba", "x", null, "c"))));
  }
}
