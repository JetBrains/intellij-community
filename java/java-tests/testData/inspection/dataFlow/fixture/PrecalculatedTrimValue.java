import java.util.Map;
import org.jetbrains.annotations.*;

class Test {
  void test(String str) {
    if (str.stripLeading().length() > 2) {
      if (<warning descr="Condition 'str.equals(\" \")' is always 'false'">str.equals("  ")</warning>) {
        if (str.stripLeading().isEmpty()) {

        }
      }
    }
    if (str.length() < 10) {
      if (<warning descr="Condition 'str.trim().length() == 10' is always 'false'">str.trim().length() == 10</warning>) {

      }
    }
    if (str.isEmpty() && <warning descr="Condition 'str.isBlank()' is always 'true' when reached">str.isBlank()</warning>) {

    }
    if (str.equals("  XYZ  ")) {
      if (<warning descr="Condition 'str.stripTrailing().equals(\" XYZ\")' is always 'true'">str.stripTrailing().equals("  XYZ")</warning>) {

      }
    }
  }

  private static @Nullable Object getDescription(Map<String, Object> descriptionAware) {
    if (descriptionAware == null) return null;

    final Object rawDescription = descriptionAware.get("description");
    if (rawDescription instanceof String description) {
      if (!description.trim().isEmpty()) {
        final boolean multiLine = description.contains("\n");
        if (multiLine) {
          description = "\n" + description.trim() + "\n";
        }
        if (<warning descr="Condition 'description.equals(\"\n\")' is always 'false'">description.equals("\n")</warning>) {
          
        }
        return 123;
      }
    }
    return null;
  }

}
