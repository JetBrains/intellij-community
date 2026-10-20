package foo.copy.past;

import static java.lang.String.format; //static already have
import static java.lang.String.valueOf; //static conflict
import static java.util.List.of;//static import

import java.util.ArrayList; // conflict
import java.util.HashSet; //already have
import java.util.List; // usual import

class From {
  public static void main(String[] args) {
    <selection>
    List<String> a2 = new ArrayList<>();
    new HashSet<>(a2);

    of("a", "b", "c");
    format("1");
    valueOf(1);
    </selection>
  }
}