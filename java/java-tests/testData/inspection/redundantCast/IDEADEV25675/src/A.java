public class Test {
    public void foo(Object[] array) {
        ((String[]) array)[0] = " ";
    }

    public void bar(String[] array) {
        ((Object[]) array)[0] = new Object();
    }
}