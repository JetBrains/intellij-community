import java.util.ArrayList;
import java.util.Optional;

public class LastMigration {

  void m(ArrayList<String> ss, String a, String b) {
    Optional<String> last = ss.stream().reduce((a1, b1) -> b1);
  }
}
