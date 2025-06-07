import java.util.function.BooleanSupplier;

class FieldWriteInLambda {
  private String myField;

  FieldWriteInLambda(boolean b) {
    BooleanSupplier r = () -> {
      myField = Math.random() > 0.5 ? "foo" : <warning descr="Assigning 'null' value to non-annotated field">null</warning>;
      return myField != null;
    };
    if (b) {
      r.getAsBoolean();
      if (myField == null) {

      }
    } else if (r.getAsBoolean()) {
      if (myField == null) {

      }
    }
  }
}