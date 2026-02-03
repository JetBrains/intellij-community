import java.util.*;

class Main {
    public class <caret>MyComparator implements Comparator<String> {
        @Override
        public int compare(String s1, String s2) {
            return 0;
        }
    }
    
    void sort(List<String> scores) {
        scores.sort(new MyComparator());
    }
}