import java.util.*;

class T {
    void f(Set<String> t, String[] f, int n, int k) {
        <caret>for (int i = 0; i < n + k; i++) {
            t.add(f[i + -k]);
        }
    }
}