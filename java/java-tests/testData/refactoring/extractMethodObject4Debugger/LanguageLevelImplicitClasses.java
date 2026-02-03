
public static void main(String[] args) throws IOException {
  List<String> strings = readNonEmptyLines();
  strings.forEach(System.out::println);
}

private static List<String> readNonEmptyLines() throws IOException {
  try (BufferedReader reader = new BufferedReader(new FileReader("somePath"))) {
    <caret>
    return reader.lines().filter(l -> !l.isBlank()).filter(l -> !l.startsWith("a")).filter(l -> l.length() > 3).toList();
  }
}
