import java.util.*;

interface VcsRoot {
}

interface SVcsRoot extends VcsRoot {
}

interface A {
    List<? extends VcsRoot> getVcsRoots();
}

interface B {
    List<SVcsRoot> getVcsRoots();
}

interface F1 extends A, B {
}

class G {
    void f(F1 o) {
        SVcsRoot r = o.getVcsRoots().get(0);
    }
}
