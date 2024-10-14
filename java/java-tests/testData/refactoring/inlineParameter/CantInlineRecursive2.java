import java.lang.Integer;
import java.util.*;
import java.util.ArrayList;

public class Aphrodite {

  public void recursiveCountdown(Integer a<caret>){
    if (a == 1){
      return;
    }
    recursiveCountdown(a-1);
  }
}
