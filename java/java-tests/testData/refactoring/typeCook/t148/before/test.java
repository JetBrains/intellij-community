interface Comparable<T> {}
interface Collection<T> {}
interface List<T> extends Collection<T>{}

class Test
{
    void test(Collection collection) {

        Collections.sort((List)collection);
        if (collection instanceof List) {
            Collections.sort((List)collection);
        }
    }

    public static <T extends Comparable<? super T>> void sort(List<T> list) {
    }
}