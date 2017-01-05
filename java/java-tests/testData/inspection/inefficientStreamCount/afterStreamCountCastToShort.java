// "Replace Collection.stream().count() with Collection.size()" "true"

import java.util.Arrays;

class Test {
    short cnt() {
        /*before dot*/
        //after dot
        return (short) Arrays.asList('d', 'e', 'f').size();
    }
}