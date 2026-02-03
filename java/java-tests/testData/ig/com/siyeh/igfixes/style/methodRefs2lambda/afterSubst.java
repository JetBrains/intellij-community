// "Replace method reference with lambda" "true-preview"
import java.util.*;

class Bar {
    public int xxx(Bar p) { return 1; }
}

class Test {
    Comparator<Bar> comparator = (bar, p) -> bar.xxx(p);
}