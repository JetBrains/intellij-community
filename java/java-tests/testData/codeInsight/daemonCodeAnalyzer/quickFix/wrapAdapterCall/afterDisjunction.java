// "Adapt using 'Collections.singletonList()'" "true-preview"
import java.util.*;

class Test {
  List<Throwable> test() {
    try {
      System.out.println();
    }
    catch (Exception | Error ex) {
      return Collections.singletonList(ex);
    }
    return null;
  }
}