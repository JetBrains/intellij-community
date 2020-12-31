import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

class Main {
  void string() {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    String s = <warning descr="Inefficient conversion from ByteArrayOutputStream">new String(baos.toByteArray())</warning>;
  }

  void charset() {
    Charset charset = Charset.defaultCharset();

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    String s = new String(baos.toByteArray(), charset);
  }

  void charsetName() throws UnsupportedEncodingException {
    String csn = "ISO-8859-1";

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    String s = <warning descr="Inefficient conversion from ByteArrayOutputStream">new String(baos.toByteArray(), csn)</warning>;
  }


}