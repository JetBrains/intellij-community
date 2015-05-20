// "Create enum constant 'CRIME'" "true"
import java.util.Arrays;
import java.util.List;

import static AutomaticTypeInference.E.*;

class AutomaticTypeInference {

  AutomaticTypeInference(List<E> gs) {
  }

  public static void main(String[] args) {

    new AutomaticTypeInference(Arrays.asList(ACTION, CRIME));

  }

  enum E {
    ACTION, CRIME;
  }
}