import java.util.Comparator;

class Main2 {
    static class OfRef<T> {
        public OfRef() {
             this((Comparator<? super T>)  naturalOrder());
        }

        public OfRef(Comparator<? super T> comparator) {
        }
        static <K extends Comparable<? super K>> Comparator<K> naturalOrder() {
            return null;
        }
    }
}
