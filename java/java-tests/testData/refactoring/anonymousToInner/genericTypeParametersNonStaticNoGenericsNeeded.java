import java.util.*;

class A<K, V> {
    public Iterator<Map.Entry<K, V>> iterator(long revision) {
        return new <caret>Iterator<Map.Entry<K, V>>() {
            public boolean hasNext() {
                return false;
            }

            public Map.Entry<K, V> next() {
                return null;
            }

            public void remove() {
            }
        };
    }
}