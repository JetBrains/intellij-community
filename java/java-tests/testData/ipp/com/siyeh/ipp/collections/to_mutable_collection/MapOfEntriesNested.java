import java.util.*;
import static java.util.Map.entry;

class MapOfEntriesNested {
  Map<Integer, String> BINDINGS = <caret>Map.ofEntries(
    entry(1, "one"),
    entry(2, "two")
  );
}