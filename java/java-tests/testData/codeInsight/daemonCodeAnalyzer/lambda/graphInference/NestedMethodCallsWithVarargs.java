
import java.util.Map;

class Test {
  static {
    map("Account", map(
          "Email", map("email", "myemail@domain.com"),
          "firstname", "momo",
          "lastname", "this argument makes no difference"
        ),
        "akey", "makes no difference");

    map("Account", map(
          "Email", map("email", "myemail@domain.com", "aKey", "commenting out does not help"),
          "firstname", "momo",
          "lastname", "this argument makes no difference"
        ),
        "akey", "makes no difference"
    );
  }

  public static <K, V> Map<K, V> map(K key, V value, Object... args) {
    return null;
  }

  public static <K, V> Map<K, V> map(K key, V value) {
    return map(key, value, new Object[]{});
  }
}