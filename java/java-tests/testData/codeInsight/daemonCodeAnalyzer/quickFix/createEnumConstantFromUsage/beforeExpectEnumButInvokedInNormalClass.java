// "Create enum constant 'CRIME'" "false"
import java.util.Arrays;
import java.util.List;

class AutomaticTypeInference {

  AutomaticTypeInference(List<E> gs) {
  }

  public static void main(String[] args) {

    new AutomaticTypeInference(Arrays.asList(E.ACTION, CRI<caret>ME));

  }

  enum E {
    ACTION;
  }
}