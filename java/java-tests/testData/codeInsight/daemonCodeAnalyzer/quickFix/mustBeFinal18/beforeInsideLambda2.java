// "Transform 'i' into final one element array" "true-preview"
class Main {
    void test() {
        int i = 42;
        Runnable runnable1 = () -> System.out.println(i);
        Runnable runnable2 = () -> System.out.println(i<caret>++);
        i = 0;
    }
}