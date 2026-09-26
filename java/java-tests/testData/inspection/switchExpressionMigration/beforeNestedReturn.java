// "Replace with 'switch' expression" "true"

class NestedReturn {
  public static int parse(String value) {
    switc<caret>h (value) {
      case "string":
        return 1;
      case "boolean":
        return 1;
      default: {
        String trimmed = value.trim();
        if (trimmed.endsWith("2113[]")) {
          byte ops = 1;
          return ~ops;
        }
        throw new IllegalArgumentException(trimmed);
      }
    }
  }
}