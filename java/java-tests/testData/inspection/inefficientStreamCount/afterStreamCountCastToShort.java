// "Replace with 'Collection.size()'" "true-preview"

import java.util.Arrays;

class Test {
    short cnt() {
        /*before dot*/
        //after dot
        return (short) Arrays.asList('d', 'e', 'f').size();
    }
}