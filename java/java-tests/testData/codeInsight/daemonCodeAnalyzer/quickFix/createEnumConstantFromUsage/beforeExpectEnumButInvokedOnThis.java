// "Create enum constant 'CRIME'" "false"
import java.util.Arrays;
import java.util.List;

class AutomaticTypeInference {

  AutomaticTypeInference(List<E> gs) {
  }

  {

    new AutomaticTypeInference(Arrays.asList(E.ACTION, this.CRI<caret>ME));

  }

  enum E {
    ACTION;
  }
}