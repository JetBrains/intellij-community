// "Fix all 'Suspicious byte value returned from 'InputStream.read()'' problems in file" "true"
import java.io.InputStream;

class MyInputStream extends InputStream {
  Byte x = 0;
  byte y = 0;

  static byte getByte() {
    return 1;
  }

  @Override
  public int read() throws IOException {
    if (true) {
      return /*comment1*/ y & 0xFF /*comment2*/;
    }
    if (true) {
      return getByte() & 0xFF;
    }
    if (true) {
      return -1;
    }
    return (x) & 0xFF;
  }
}
