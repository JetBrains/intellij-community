import java.util.*;

class T {
    void f(Set<String> t, String[] f, int n) {
        <caret>for (int i = -1; i < n * 2; i++) {
            t.add(f[i+1]);
        }
    }
}