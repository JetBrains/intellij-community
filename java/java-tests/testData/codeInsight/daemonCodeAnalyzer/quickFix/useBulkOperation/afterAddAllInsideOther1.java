// "Replace iteration with bulk 'Collection.addAll()' call" "true"

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
        this.addAll(c);
      return true;
    }
  }
}