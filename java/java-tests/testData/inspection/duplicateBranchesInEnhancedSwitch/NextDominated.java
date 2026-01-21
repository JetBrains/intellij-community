import java.math.BigDecimal;

class C {
  public Object convert2(Object value) {
    switch (value) {
      case BigDecimal v -> <weak_warning descr="Duplicate branch in 'switch'">{
        return  v.toString() + v.toString();
      }</weak_warning>
      case Number t -> {
        return t.toString() + t.toString();
      }
      default -> {
        return value;
      }
    }
  }

  public Object convertNonDominated(Object value) {
    switch (value) {
      case BigDecimal v -> {
        return  v.toString() + v.toString();
      }
      case Runnable t -> {
        return t.toString() + t.toString();
      }
      default -> {
        return value;
      }
    }
  }
}