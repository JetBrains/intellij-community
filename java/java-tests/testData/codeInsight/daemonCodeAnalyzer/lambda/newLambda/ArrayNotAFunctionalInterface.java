
import java.util.concurrent.Callable;

class Test {
  public static <T> void execute(Callable<T>[] cmds) { }

  public static void main(String[] args) throws Exception{
    execute<error descr="'execute(java.util.concurrent.Callable<T>[])' in 'Test' cannot be applied to '(<lambda expression>)'">(() -> null)</error>;
  }
}
