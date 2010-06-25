// "Permute arguments" "true"
public class S {
    void f(S k, int i, String s, Object o) {
        f(this, 1, "", new Object());<caret>
    }
}
