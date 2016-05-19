
import java.io.IOException;
import java.util.List;

class Reproduction
{
  public static List<Reproduction> a() throws IOException {
    return exe(connection -> {
      Object stmt = null;
      return st(Reproduction.class, 1, "");
    });
  }

  public static List<Reproduction> b() throws IOException {
    return exe(connection -> {
      return st(Reproduction.class, 1, "");
    });
  }

  static <K> List<K> st(Class<K> c, Object... o) throws IOException {
    return null;
  }

  static <V> V exe(VFunction<V> vFunction) throws IOException {
    return vFunction.apply("");
  }

  interface VFunction<V> {
    V apply(String s) throws IOException;
  }
}
