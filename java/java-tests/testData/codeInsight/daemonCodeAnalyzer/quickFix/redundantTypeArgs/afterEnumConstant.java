// "Remove type arguments" "true"

import java.util.Collections;
import java.util.List;

enum ExposeRemotely {
  NEVER("never", Collections.emptyList());
  ExposeRemotely(String name, List<String> keys) {}
}
