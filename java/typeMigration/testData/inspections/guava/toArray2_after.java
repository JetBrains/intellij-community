import java.util.List;
import java.util.stream.Stream;

public class B {
  public List<String>[] toCollectionArray(Stream<List<String>> p, Class<List<String>> c) { return p.toArray(i -> (List<String>[]) new List[i]); }
}
