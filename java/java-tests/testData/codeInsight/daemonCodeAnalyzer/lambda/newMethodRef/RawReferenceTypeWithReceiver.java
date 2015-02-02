import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

class Main {
  public static void main(String[] args) {
    Stream<List<? extends Main>> listStream = new ArrayList<List<? extends Main>>().stream();
    Stream<? extends Main> l1 = listStream.flatMap(Collection::stream);
    Stream<? extends Main> l2 = listStream.flatMap(List::stream);
  }
}