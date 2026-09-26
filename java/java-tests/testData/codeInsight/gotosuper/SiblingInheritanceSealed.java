package z;

sealed interface Derivative permits Option {
  java.time.LocalDate getExpiration();
}
non-sealed interface Option extends Derivative {
}

class AbstractDerivative {
  public java.time.LocalDate <caret>getExpiration() {
    return java.time.LocalDate.MAX;
  }
}

class OptionImpl extends AbstractDerivative implements Option {
}
