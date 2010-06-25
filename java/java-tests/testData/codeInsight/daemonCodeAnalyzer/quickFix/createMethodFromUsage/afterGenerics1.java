// "Create Method 'bar'" "true"
public class Test {
    <T extends String> void foo (T t1, T t2) {
        bar (t1, t2);
    }

    private <T extends String> void bar(T t1, T t2) {
        <selection>//To change body of created methods use File | Settings | File Templates.<caret></selection>
    }
}