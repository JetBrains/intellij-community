// "Replace loop with 'Arrays.fill()' method call" "true"
package pack;

public class HashMap<K,V> {
  static class Node<K,V> {
    final int hash;
    final K key;
    V value;
    Node<K,V> next;

    Node(int hash, K key, V value, Node<K,V> next) {
      this.hash = hash;
      this.key = key;
      this.value = value;
      this.next = next;
    }

  }

  transient Node<K,V>[] table;

  transient int size;

  transient int modCount;
}

  public void clear() {
    Node<K,V>[] tab;
    modCount++;
    if ((tab = table) != null && size > 0) {
      size = 0;
      for (<caret>int i = 0; i < tab.length; ++i)
        tab[i] = null;
    }
  }
}