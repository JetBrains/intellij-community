// "Replace lambda with method reference" "true"
import java.util.prefs.Preferences;

class Test {

  private Preferences preferences;

  {
    foo(short.class, (Writer<Short>) Preferences::putInt);
  }

  private <T> void foo(Class<T> type, Writer<T> writer) {}

  interface Writer<T> {
    void write(Preferences preferences, String key, T value);
  }
}