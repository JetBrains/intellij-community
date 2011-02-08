class List<T> {}
class ArrayList<T> extends List<T> {}
class Comparator<T> {}

public class TokenStreamRewriteEngine {
    public static <T> T binarySearch(List<? extends T> list, T key, Comparator<? super T> c) {
        return null;
    }

    protected void addToSortedRewriteList(String programName, String op, Comparator comparator) {
        List rewrites = new ArrayList();
        <ref>binarySearch(rewrites, op, comparator);
    }
}