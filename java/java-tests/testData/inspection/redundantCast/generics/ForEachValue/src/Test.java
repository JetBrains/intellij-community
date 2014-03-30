import java.util.ArrayList;
import java.util.List;

public class RedundantCast {
    void redundantCasts() {
       List<String> list = new ArrayList<>();
       for (String s : (ArrayList<String>) list) {}

       Object o = new ArrayList<>();
       for (String s : (ArrayList<String>) o) {}
    }
}
