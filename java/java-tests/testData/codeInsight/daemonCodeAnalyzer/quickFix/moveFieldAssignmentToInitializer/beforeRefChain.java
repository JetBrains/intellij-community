// "Move assignment to field declaration" "false"
public class Test {
  final static String FIELD;
  static {
    String data = "   foo   ";
    <caret>FIELD = data.trim();
  }
}
