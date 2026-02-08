// "Delete redundant 'switch' branch" "GENERIC_ERROR_OR_WARNING"
import java.math.BigDecimal;

class C {
  public Object convert2(Object value) {
    switch (value) {
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