// "Remove duplicates" "true"
import java.util.Comparator;

class X implements Comparator<Integer>, Comparator, Comparator<String><caret>, Comparator<Double> {}