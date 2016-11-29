// "Replace with addAll" "false"

import java.util.*;

public class Main {
  static class MyList extends AbstractCollection<String> {
    @Override
    public Iterator<String> iterator() {
      return Collections.emptyIterator();
    }

    @Override
    public int size() {
      return 0;
    }

    @Override
    public boolean addAll(Collection<? extends String> c) {
      for (String e : <caret>c) {
        add(e);
      }
      return true;
    }
  }
}