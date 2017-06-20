// "Replace Collection.stream().count() with Collection.size()" "true"

import java.util.Arrays;

class Test {
    /*count*/
    long cnt = (long) Arrays.asList('d', 'e', 'f')./*stream*/size()/*after*/;
}