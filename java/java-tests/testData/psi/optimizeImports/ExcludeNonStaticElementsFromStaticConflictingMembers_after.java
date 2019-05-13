import static java.lang.String.*;

class OptimizeImport {
  public static void main(String[] args) {
    format("foo");
    valueOf(1);
    copyValueOf(new char[0]);
  }
}