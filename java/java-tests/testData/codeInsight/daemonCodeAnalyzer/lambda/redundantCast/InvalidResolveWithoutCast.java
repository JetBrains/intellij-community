import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;


abstract class CallbackCast {
  String send(Number... actions)  {
    return executeBlocking(callback -> processNumbers(Arrays.asList(actions), (Runnable) callback));
  }

  public <T> T executeBlocking(Statement<T> statement) {
    return null;
  }

  abstract <T extends Number> List<T> processNumbers(List<T> actions, Runnable callback);
}

interface Statement<T> {
  void execute(Consumer<T> callback);
}