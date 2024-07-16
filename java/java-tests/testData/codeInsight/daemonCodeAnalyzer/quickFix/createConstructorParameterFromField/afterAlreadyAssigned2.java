// "Add constructor parameter" "true"
public class SimpleClass { // IDEA-333847
  final boolean b;
  final String s;

  public SimpleClass(boolean b, String s) {
      this.b = b;
      this.s = s;
  }

  public SimpleClass(int i, boolean b, String s) {
      this.b = b;
      this.s = s;
  }
}