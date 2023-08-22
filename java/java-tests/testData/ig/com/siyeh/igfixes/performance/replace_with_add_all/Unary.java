import java.util.*;

class T {
    void f(Set<String> t, String[] f, int n) {
        <caret>for (int i = -2; i < -n; i++) {
            t.add(f[i+3]);
        }
    }
}