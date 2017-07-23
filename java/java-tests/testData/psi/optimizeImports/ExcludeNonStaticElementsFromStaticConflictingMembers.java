import static java.lang.String.format;
import static java.lang.String.valueOf;
import static java.lang.String.copyValueOf;

class OptimizeImport {
  public static void main(String[] args) {
    format("foo");
    valueOf(1);
    copyValueOf(new char[0]);
  }
}