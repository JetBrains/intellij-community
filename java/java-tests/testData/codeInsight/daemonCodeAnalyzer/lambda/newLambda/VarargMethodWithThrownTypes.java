
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

class InferenceInVarargsParam {
  static class Pair<A, B> {
    static <T, K> Pair<T, K> create(T t, K k) {
      return null;
    }
  }
  protected void assertPathListEditor(String text, Pair<String, Boolean>... expectedTableItems) throws Exception {}

  void testPathListSettingsEditor() throws Exception {
    assertPathListEditor("",
                         Pair.create("foo", false),
                         Pair.create("bar", true),
                         Pair.create("$(PROJECT_DIR)/hello", false),
                         Pair.create("$(PROJECT_DIR)/hello", true));
  }
}
