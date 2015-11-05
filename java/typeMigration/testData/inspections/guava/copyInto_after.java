import java.util.ArrayList;
import java.util.stream.Stream;

public class Main10 {

  void m() {
    Stream<String> it = new ArrayList<String>().stream();

    ArrayList<String> al = new ArrayList<>();
    ArrayList<String> al2 = new ArrayList<>();
    it.map(s -> s + "asd").forEach((al.size() > 10 ? al : al2)::add);
  }
}