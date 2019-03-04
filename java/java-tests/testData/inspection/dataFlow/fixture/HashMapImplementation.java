import org.jetbrains.annotations.Nullable;

class IntHashMap {

  public void minTest() {
    initEntries();

    for (Entry entry : table) {
      if (entry != null) {
        Entry tmp = entry;
        Entry tmpNext;

        while (tmp != null) {
          tmpNext = tmp.next;
          tmp.next = null;
          tmp = tmpNext;
          System.out.println("tmpNext " + ((tmpNext == null) ? "is null" : "is not null"));
        }
      }
    }
  }

  private Entry[] table = new Entry[16];

  private class Entry {
    @Nullable Entry next;
  }


  private void initEntries() {
    table[0] = new Entry();
    table[0].next = new Entry();

    table[1] = new Entry();

    table[3] = new Entry();

    table[5] = new Entry();
    table[5].next = new Entry();
  }
  public static void main(String[] args) {
    IntHashMap map = new IntHashMap();
    map.minTest();
  }
}