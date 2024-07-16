// "Add constructor parameter" "true"
public class SimpleClass { // IDEA-333847
  final <caret>boolean b;
  final String s;

  public SimpleClass() {
  }

  public SimpleClass(int i, String s) {
    this.s = s;
  }
}