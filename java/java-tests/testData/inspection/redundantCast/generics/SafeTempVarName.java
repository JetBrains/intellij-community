import java.util.*;

class RedundantCast{
    <T> void foo2(List<Object[]> x) {
        for (Object[] l : x) {
           String[] s = (String[]) l;
        }
    }
}
