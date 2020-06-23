import java.util.ArrayList;
import java.util.List;

class RedundantCast {
    void redundantCasts() {
       List<String> list = new ArrayList<>();
       for (String s : (<warning descr="Casting 'list' to 'ArrayList<String>' is redundant">ArrayList<String></warning>) list) {}
       for (String s : (<warning descr="Casting '(ArrayList<String>)list' to 'ArrayList<String>' is redundant">ArrayList<String></warning>) (<warning descr="Casting 'list' to 'ArrayList<String>' is redundant">ArrayList<String></warning>) list) {}

       Object o = new ArrayList<>();
       for (String s : (ArrayList<String>) o) {}
       for (String s : (ArrayList<String>) (<warning descr="Casting 'o' to 'Object' is redundant">Object</warning>) o) {}
    }
}
