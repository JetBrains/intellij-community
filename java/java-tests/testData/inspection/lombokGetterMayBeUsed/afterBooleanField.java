// "Use lombok @Getter for 'valid'" "true"

import lombok.Getter;

public class ClassWithBoolean {
  private int fieldWithoutGetter;
  @Getter
  private boolean valid;

}