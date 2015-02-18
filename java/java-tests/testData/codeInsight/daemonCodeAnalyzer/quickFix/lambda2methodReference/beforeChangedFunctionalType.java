// "Replace lambda with method reference" "true"
import java.util.prefs.Preferences;

class Test {

  private Preferences preferences;

  {
    foo(short.class, (p, k, v) -> p.put<caret>Int(k, v));
  }

  private <T> void foo(Class<T> type, Writer<T> writer) {}

  interface Writer<T> {
    void write(Preferences preferences, String key, T value);
  }
}