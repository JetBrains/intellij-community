class G<T> {}

public class C {

    <T> T f (G<T> l) { return null;}

    void foo (G l) {
        String s = <ref>f(l);
    }
}
