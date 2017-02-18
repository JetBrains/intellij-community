import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;

import java.util.ArrayList;

public class LastMigration {

  void m(ArrayList<String> ss, String a, String b) {
    Optional<String> last = FluentIterable.fr<caret>om(ss).last();
  }
}
