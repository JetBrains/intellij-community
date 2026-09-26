// "Transform 'i' into final one element array" "true-preview"
class Main {
    void test() {
        final int[] i = {42};
        Runnable runnable1 = () -> System.out.println(i[0]);
        Runnable runnable2 = () -> System.out.println(i[0]++);
        i[0] = 0;
    }
}