import java.util.LinkedList;
import java.util.function.Function;
import java.util.function.Predicate;

class Main {
  {
    new LinkedList<Object>().forEach((value)->{
      new LinkedList<Object>().stream().filter((c)->{
        return c == null;
      });
    });
  }
}

