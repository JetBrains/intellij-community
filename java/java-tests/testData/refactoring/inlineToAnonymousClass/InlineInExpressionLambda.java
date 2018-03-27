import java.util.function.Function;

class InlineFromLambda {
  private static Function<String, Object> func(int p) {
    return s -> new Co<caret>mmand(s, p);
  }

  private static class Command {
    public Command(String name, int p) {
    }
  }
}