// "Replace loop with 'Arrays.fill()' method call" "true"
import java.util.Arrays;
import java.util.List;

public class Test {

  public static<T> void fill(T[] data, T value) {
    for(int <caret>idx = 0; (/*comment*/data).length > idx; idx+=1) {
      /*in body*/
      data[/*in lvalue*/idx] = /*in rvalue*/value;
    }
  }
}