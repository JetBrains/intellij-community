// "Fix all 'Redundant 'String' operation' problems in file" "true"
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

class Main {
  void foo(char[] chars, byte[] bytes, int[] ints) {
    String s1 = new String(bytes, 0, bytes.length<caret>);
    String s2 = new String(chars, 0, chars.length);
    String s3 = new String(bytes, 0, bytes.length, Charset.defaultCharset());
    String s4 = new String(bytes, 0, bytes.length, StandardCharsets.ISO_8859_1);

    String s5 = new String(ints, 0, ints.length);
    String s6 = new String(chars, 0, bytes.length);
    String s7 = new String(bytes, 1, bytes.length, Charset.defaultCharset());
    String s8 = new String(bytes, 0, bytes.length - 1, StandardCharsets.ISO_8859_1);
  }
}
