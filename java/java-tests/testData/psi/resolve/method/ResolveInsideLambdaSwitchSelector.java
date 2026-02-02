import java.util.List;
import java.util.stream.Collectors;

class Diagnostic {
  private final String code;

  public Diagnostic(String code) {
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}

public class ResolveInsideLambdaSwitchSelector {
  public static void main(String[] args) {
  }

  public static void test11(List<Diagnostic> diags) {
    List<Diagnostic> filtered = diags.stream().filter(diag -> {
      switch (diag.getCode()) {
        case "foo" -> {
          return false;
        }
      }
      return true;
    }).col<caret>lect(Collectors.toUnmodifiableList());
  }
}

