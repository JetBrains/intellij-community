// "Permute arguments" "true"
public class S {
    void f(S k, int i, String s, Object o) {
        f(this,"",new Object(),1);
        <caret>f(this,new Object(),1,"");
    }
}
