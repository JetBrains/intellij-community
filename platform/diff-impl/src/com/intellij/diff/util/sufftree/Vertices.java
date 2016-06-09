package com.intellij.diff.util.sufftree;

public class Vertices {
  public abstract static class Entry {
    private Entry link;

    public abstract int hashCode2();
  }

  private Entry[] table;
  private int size;

  public Vertices(int size) {
    this.size = size;
    table = new Entry[size];
  }

  public void put(Entry value) {
    int code = (value.hashCode2() & 0x7FFFFFFF) % size;
    value.link = table[code];
    table[code] = value;
  }

  public Entry search(Entry value) {
    int code = (value.hashCode2() & 0x7FFFFFFF) % size;
    for (Entry e = table[code]; e != null; e = e.link) {
      if (value.equals(e)) {
        return e;
      }
    }

    return null;
  }

  public void delete(Entry value) {
    int code = (value.hashCode2() & 0x7FFFFFFF) % size;

    if (table[code] == value) {
      table[code] = table[code].link;
      return;
    }

    for (Entry e = table[code]; e != null; e = e.link) {
      if (e.link == value) {
        e.link = value.link;
        return;
      }
    }

    throw new IllegalArgumentException("Entry not found");
  }
}
