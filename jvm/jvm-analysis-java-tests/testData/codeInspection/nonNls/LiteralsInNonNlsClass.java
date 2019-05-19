import org.jetbrains.annotations.NonNls;

@NonNls
public class LiteralsInNonNlsClass {
  String field = "field";

  String method(String param) {
    param = "param";
    field = "field";
    String var = "var";
    var.concat("concat");
    var.equals("equals");
    return "return";
  }
}