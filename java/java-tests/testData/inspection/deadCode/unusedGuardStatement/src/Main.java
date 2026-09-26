import java.io.Serializable;

class Test {

  @SuppressWarnings("unused")
  private <T extends Serializable> Object toConflictDetail(String controlClass, T conflict) {
    return switch (conflict) {
      case String ruleConflict when hasSameControlClass(controlClass, ruleConflict) -> conflict;
      case Number nestingConflict when hasSameControlClass(controlClass, nestingConflict) -> null;
      default -> null;
    };
  }

  private static boolean hasSameControlClass(String controlClass, Object ruleConflict) {
    return controlClass.equals(ruleConflict);
  }
}