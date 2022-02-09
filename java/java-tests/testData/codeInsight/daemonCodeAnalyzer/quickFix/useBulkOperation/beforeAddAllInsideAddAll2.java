// "Replace iteration with bulk 'Collection.addAll()' call" "false"

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
      for (String e : c) {
        super.a<caret>dd(e);
      }
      return true;
    }
  }
}