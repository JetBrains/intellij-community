// "Replace with 'Collection.size()'" "true"

import java.util.Arrays;

class Test {
    short cnt() {
        return (short) Arrays.asList('d', 'e', 'f').stream()/*before dot*/.//after dot
         c<caret>ount();
    }
}