import java.util.Map;
import org.jetbrains.annotations.*;

class Test {
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
