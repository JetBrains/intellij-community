import java.util.*;

class A {
    private Inner b = new Inner();

    private class <caret>Inner implements Comparator<String> {
        public int compare(String s1, String s2) {
            return 0;
        }
    }
}