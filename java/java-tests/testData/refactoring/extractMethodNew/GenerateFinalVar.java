import java.util.*;

public class Foo {

  public void foo(List<String> strings) {
    <selection>int oddNumbered = 0;
    if (strings != null) {
      for (String string : strings) {
        if (string.length() % 2 == 1) {
          oddNumbered++;
        }
      }
    }</selection>
    setOddNumbered(oddNumbered);
  }

  private void setOddNumbered(int oddNumbered) {
  }
}