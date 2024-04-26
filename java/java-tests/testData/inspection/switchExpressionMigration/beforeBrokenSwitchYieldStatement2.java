// "Replace with 'switch' expression" "false"
import java.util.*;

class NestedReturn {
  public static List<Class<?>> parse(String value) {
    switc<caret>h (value) {
      case "string":
        return List.of(String.class);
      case "boolean":
        return List.of(Boolean.class);
      default: {
        String trimmed = value.trim();
        if (trimmed.endsWith("2113[]")) {
          List<Class<?>> children = new ArrayList<>();
          children.add(List.class);
          children.addAll(parse(trimmed.substring(0, trimmed.length() - 2)));
          return {};
        }
        throw new IllegalArgumentException(trimmed);
      }
    }
  }
}