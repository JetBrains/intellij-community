import java.util.List;

class Test<T> {
    void test(List<T> list, T element){
        extracted(list, element);
    }

    private static <T> void extracted(List<T> list, T element) {
        list.add(element);
    }
}