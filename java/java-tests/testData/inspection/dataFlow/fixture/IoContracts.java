import java.io.IOException;
import java.io.InputStream;

public class IoContracts {
  int x;
  
  void simple() {
    x = 1;
    System.out.println(x);
    if (<warning descr="Condition 'x == 1' is always 'true'">x == 1</warning>) {
      System.out.println("Still 1");
    }
  }
  
  void inputStream(byte[] data, short[] data2, InputStream in) throws IOException {
    if (data[0] == 0) return;
    if (data2[0] == 0) return;
    if (in.read() == -1) return;
    if (<warning descr="Condition 'data[0] == 0' is always 'false'">data[0] == 0</warning>) return;
    if (<warning descr="Condition 'data2[0] == 0' is always 'false'">data2[0] == 0</warning>) return;
    if (in.read(data) == 0) return;
    if (data[0] == 0) return;
    if (<warning descr="Condition 'data2[0] == 0' is always 'false'">data2[0] == 0</warning>) return;
  }
}