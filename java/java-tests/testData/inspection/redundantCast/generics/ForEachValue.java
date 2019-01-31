import java.util.ArrayList;
import java.util.List;

class RedundantCast {
    void redundantCasts() {
       List<String> list = new ArrayList<>();
       for (String s : (<warning descr="Casting 'list' to 'ArrayList<String>' is redundant">ArrayList<String></warning>) list) {}

       Object o = new ArrayList<>();
       for (String s : (ArrayList<String>) o) {}
    }
}
