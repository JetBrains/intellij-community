import java.util.List;

class Some {
  public static void appendTokenTypes(StringBuilder sb, List<String> tokenTypes) {
    for (int count = 0, line = 0, size = tokenTypes.size(); count < size; count++) {
      boolean newLine = count == 2 || <warning descr="Condition 'line > 0 && (count - 2) % 6 == 0' is always 'false' when reached"><warning descr="Condition 'line > 0' is always 'false' when reached">line > 0</warning> && (count - 2) % 6 == 0</warning>;
      newLine &= (size - count) > 2;
    }
  }

}
