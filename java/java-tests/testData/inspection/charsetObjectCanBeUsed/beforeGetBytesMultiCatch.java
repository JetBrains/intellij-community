// "Replace with 'StandardCharsets.US_ASCII'" "true"
import java.io.*;

class Test {
  void test(String s) {
    byte[] bytes = null;
    try {
      string = s.getBytes("US-<caret>ASCII");
    }
    catch (UnsupportedEncodingException | StackOverflowError exception) {
      exception.printStackTrace();
    }
    if(bytes[0] == 'a') {
      System.out.println("A-a-a!");
    }
  }
}