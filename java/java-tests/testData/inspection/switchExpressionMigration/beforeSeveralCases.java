// "Migrate to enhanced switch with rules" "true-preview"
public class IDEA_372961 {
  public enum SomeType {
    TYPE_1,
    TYPE_2,
    TYPE_UNKNOWN;


    public static SomeType fromValue(String value) {
      return switch<caret> (value) {
        case "type_1":
        case "type_2":
        case "type_3":
          yield TYPE_1;
        default:
          yield TYPE_UNKNOWN;
      };
    }
  }
}