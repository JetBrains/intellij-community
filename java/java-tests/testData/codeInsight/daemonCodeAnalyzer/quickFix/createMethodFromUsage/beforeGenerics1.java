// "Create method 'bar'" "true-preview"
public class Test {
    <T extends String> void foo (T t1, T t2) {
        <caret>bar (t1, t2);
    }
}