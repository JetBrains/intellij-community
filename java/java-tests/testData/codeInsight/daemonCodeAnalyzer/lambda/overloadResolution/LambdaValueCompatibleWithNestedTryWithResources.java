
import java.util.Collections;
import java.util.Set;

class Test {
  Database database;

  Set<Long> getItems() {
    return database.perform(connection -> {
      try (AutoCloseable c = null) {
        try (AutoCloseable d = null) {
          return Collections.emptySet();
        }
      }
    });
  }

  public interface Database {
    <V> V perform(BusinessLogic<V> logic);
  }

  public interface BusinessLogic<V> {
    V execute(String connection) throws Exception;
  }
}