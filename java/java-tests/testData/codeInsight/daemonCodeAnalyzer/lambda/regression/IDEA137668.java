import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
class Test {

  private InputStream stream;

  public int read(byte[] data, int offset, int len) throws IOException {
    return apply(() -> {
      if (stream == null || stream.available() < 0) {
        throw new FileNotFoundException();
      }
      for (long t = System.currentTimeMillis(); stream != null && stream.available() >= 0; ) {
        if (System.currentTimeMillis() - t >= 10_000) {
          throw new InterruptedIOException("Timeout exceeded");
        }
        final int count = stream.available();
        if (count > 0) {
          final int n = Math.min(len, count);
          final byte[] buf = new byte[n];
          final int c = stream.read(buf);
          System.arraycopy(buf, 0, data, offset, c);
          return c;
        } else {
          Thread.sleep(1L);
        }
      }
      throw new FileNotFoundException();
    });
  }

  synchronized <T> T apply(SerialPortAction<T> action) throws IOException {
    try {
      return action.apply();
    } catch (InterruptedException x) {
      final InterruptedIOException y = new InterruptedIOException(x.getMessage());
      y.initCause(x);
      throw y;
    }
  }

  @FunctionalInterface
  interface SerialPortAction<T> {

    T apply() throws IOException, InterruptedException;
  }
}