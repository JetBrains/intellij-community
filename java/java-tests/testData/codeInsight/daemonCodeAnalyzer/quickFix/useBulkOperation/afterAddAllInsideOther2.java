// "Replace iteration with bulk 'Collection.addAll()' call" "true-preview"

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

    public boolean myAdd(Collection<? extends String> c) {
        super.addAll(c);
      return true;
    }
  }
}