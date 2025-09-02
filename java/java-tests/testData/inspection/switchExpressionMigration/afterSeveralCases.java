// "Migrate to enhanced switch with rules" "true-preview"
public class IDEA_372961 {
  public enum SomeType {
    TYPE_1,
    TYPE_2,
    TYPE_UNKNOWN;


    public static SomeType fromValue(String value) {
      return switch (value) {
          case "type_1", "type_2", "type_3" -> TYPE_1;
          default -> TYPE_UNKNOWN;
      };
    }
  }
}