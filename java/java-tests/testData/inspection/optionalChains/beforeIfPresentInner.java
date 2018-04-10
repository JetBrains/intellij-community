// "Replace null check with ifPresent()" "false"
import java.util.Optional;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Optional;

public class Test {
  static  Optional<String> getFoo(Class<?> type) {
    return Optional.empty();
  }
  static  Optional<String> getBar(Field field) {
    return Optional.empty();
  }

  public static void main(String[] args) {
    final Class<?> cls = ArrayList.class;
    for (Field f : cls.getFields()) {
      final String fieldType = getFoo(f.getType()).orElseGet(() -> getBar(f).<caret>orElse(null));
      if (fieldType != null) {
        System.out.println(fieldType);
      }
    }
  }
}