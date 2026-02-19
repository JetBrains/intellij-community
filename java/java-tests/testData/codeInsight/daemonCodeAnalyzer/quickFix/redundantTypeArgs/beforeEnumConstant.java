// "Remove type arguments" "true-preview"

import java.util.Collections;
import java.util.List;

enum ExposeRemotely {
  NEVER("never", Collections.<Str<caret>ing>emptyList());
  ExposeRemotely(String name, List<String> keys) {}
}
