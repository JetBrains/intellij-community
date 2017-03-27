// "Replace loop with Arrays.setAll" "false"
import java.util.Arrays;
import java.util.List;

public class Test {
  public void test(List<Byte> bytes) {
    byte[] arr = new byte[bytes.size()];
    for(int <caret>i = 0; arr.length > i; i++) {
      arr[i] = bytes.get(i);
    }
    System.out.println(Arrays.toString(arr));
  }
}