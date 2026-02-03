// "Create enum constant 'CRIME'" "true"
import java.util.*;

class AutomaticTypeInference {

  AutomaticTypeInference(List<E> gs) {
  }

  public static void main(String[] args) {

    new AutomaticTypeInference(Arrays.asList(E.ACTION, E.CRI<caret>ME));

  }

  enum E {
    ACTION;
  }
}