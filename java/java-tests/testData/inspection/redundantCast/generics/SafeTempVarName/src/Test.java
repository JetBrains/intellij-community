import java.util.*;

public class RedundantCast{
    <T> void foo2(List<Object[]> x) {
        for (Object[] l : x) {
           String[] s = (String[]) l;
        }
    }
}
