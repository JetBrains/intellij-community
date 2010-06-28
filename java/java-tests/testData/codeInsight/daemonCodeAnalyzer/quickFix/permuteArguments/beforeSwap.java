// "Permute arguments" "true"
public class S {
    void f(S k, int i, String s, Object o) {
        f("",1,this,new Object());<caret>
    }
}
