// "Convert implicitly declared class into regular class" "true-preview"

private final String field = "field";

public static void main() {
  System.out.println("Hello, world!");
}

public static void mai<caret>n(String[] args) {
  System.out.println("Hello, world!");
}
