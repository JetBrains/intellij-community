
import java.util.concurrent.Callable;

class Test {
  public static <T> void execute(Callable<T>[] cmds) { }

  public static void main(String[] args) throws Exception{
    execute(<error descr="Callable<T>[] is not a functional interface">() -> null</error>);
  }
}
