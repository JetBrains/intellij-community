import java.util.*;

public class Foo {

  public void foo(List<String> strings) {
      final int oddNumbered = newMethod(strings);
      setOddNumbered(oddNumbered);
  }

    private int newMethod(List<String> strings) {
        int oddNumbered = 0;
        if (strings != null) {
          for (String string : strings) {
            if (string.length() % 2 == 1) {
              oddNumbered++;
            }
          }
        }
        return oddNumbered;
    }

    private void setOddNumbered(int oddNumbered) {
  }
}