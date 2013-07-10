import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


public class Test {
    public void myTest(List list, String s1, String s2) {
    }

    public void myTest(Collection list, String s1, String s2) {
    }

    public void usage() {
        List list = new ArrayList();
        String aa = "AA";
        String bb = "bb";
        myTest(list, aa, bb);
        Collection col = new ArrayList();
        newMethod(aa, bb, col);
    }

    private void newMethod(String aa, String bb, Collection col) {
        myTest(col, aa, bb);
    }
}