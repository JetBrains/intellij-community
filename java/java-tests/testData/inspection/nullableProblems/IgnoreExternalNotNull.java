import java.util.Date;
import java.util.concurrent.locks.Condition;

abstract class Demo6Impl implements Condition {
  @Override
  public boolean awaitUntil(Date deadline) {
            /* This is considered to be an error in Eclipse: The second parameter is not annotated in the JDK but
             * only through the external annotations in IntelliJ. Thus, Eclipse is complaining:
             * Illegal redefinition of parameter unit, inherited method from ExecutorService does not constrain this parameter
             */
    return false;
  }
}