// "Replace iteration with bulk 'Collection.addAll()' call" "true"
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class Main {
  public void test(Integer[] arr) {
    List<Integer> result = new ArrayList<>();
    result.add(1);
    Stream.of(arr).forEachOrdered(<caret>result::add);
  }
}