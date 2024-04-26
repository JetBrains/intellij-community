// "Replace with 'switch' expression" "true"

class NestedReturn {
  public static int parse(String value) {
      return switch (value) {
          case "string" -> 1;
          case "boolean" -> 1;
          default -> {
              String trimmed = value.trim();
              if (trimmed.endsWith("2113[]")) {
                  byte ops = 1;
                  yield ~ops;
              }
              throw new IllegalArgumentException(trimmed);
          }
      };
  }
}