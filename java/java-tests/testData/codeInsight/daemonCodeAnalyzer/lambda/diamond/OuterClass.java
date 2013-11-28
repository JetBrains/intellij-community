import java.util.*;

class HT<O> {
    private Iterator<O> getIterator(int type, int count) {
      return new Enumerator<>(type, true);
    }

    private class Enumerator<T> implements Iterator<T> {

        public Enumerator(int type, boolean b) {
        }

        @Override
        public boolean hasNext() {
            return false;
        }

        @Override
        public T next() {
            return null;
        }
    }
}