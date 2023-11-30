// "Fix all 'Statement can be replaced with enhanced 'switch'' problems in file" "true"
import java.util.*;

class NestedPatterns {
  public static List<Class<?>> parse(String value) {
      return switch (value) {
          case "string" -> List.of(String.class);
          case "boolean" -> List.of(Boolean.class);
          default -> {
              String trimmed = value.trim();
              if (trimmed.endsWith("2113[]")) {
                  List<Class<?>> children = new ArrayList<>();
                  children.add(List.class);
                  children.addAll(parse(trimmed.substring(0, trimmed.length() - 2)));
                  yield children;
              }
              throw new IllegalArgumentException(trimmed);
          }
      };
  }

  public static List<Class<?>> parse2(String value) {
      return switch (value) {
          case "string" -> List.of(String.class);
          case "boolean" -> List.of(Boolean.class);
          default -> {
              String trimmed = value.trim();
              if (trimmed.endsWith("2113[]")) {
                  List<Class<?>> children = new ArrayList<>();
                  children.add(List.class);
                  children.addAll(parse(trimmed.substring(0, trimmed.length() - 2)));
                  yield children;
              }
              throw new IllegalArgumentException(trimmed);
          }
      };
  }

  public static List<Class<?>> parse3(String value) {
    switch (value) {
      case "string":
        return List.of(String.class);
      case "boolean":
        return List.of(Boolean.class);
      default:
        String trimmed = value.trim();
        if (trimmed.endsWith("2113[]")) {
          List<Class<?>> children = new ArrayList<>();
          children.add(List.class);
          break;
        }
        throw new IllegalArgumentException(trimmed);

    }
    return List.of();
  }
}