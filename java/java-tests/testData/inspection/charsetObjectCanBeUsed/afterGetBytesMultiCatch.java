// "Replace with 'StandardCharsets.US_ASCII'" "true"
import java.io.*;

class Test {
  void test(String s) {
    byte[] bytes = null;
    try {
      string = s.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }
    catch (StackOverflowError exception) {
      exception.printStackTrace();
    }
    if(bytes[0] == 'a') {
      System.out.println("A-a-a!");
    }
  }
}