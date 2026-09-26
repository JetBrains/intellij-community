import java.io.*;

public class IoContracts9 {

  public static void main(String[] args) throws IOException {
    try (InputStream stream = new FileInputStream("test.txt")) {
      byte[] bytes = new byte[100];
      while (true) {
        int read = stream.readNBytes(bytes, 0, bytes.length);
        if (<warning descr="Condition 'read == -1' is always 'false'">read == -1</warning>) {
          System.out.println("EOF");
          break;
        } else {
          System.out.println(read);
        }
      }
    }
  }

}