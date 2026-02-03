// "Replace IntStream.range().mapToObj() with Arrays.stream(strs)" "false"
import java.util.stream.IntStream;
import java.util.stream.Collectors;

class Test {
  void test() {
    String [] strs = new String[239];
    List<String> collect = IntStream.range(0, strs.length).
      <Runnable><caret>mapToObj(idx -> () -> strs[idx] = strs[idx].toUpperCase()).collect(Collectors.toList());
  }
}