
import java.io.IOException;

abstract class Ambiguity {

  public abstract <T> T executeServerOperation(ThrowableComputable<T, IOException> computable);
  public <T> T executeServerOperation(final Computable<T> computable) {
    return null;
  }

  boolean foo(Ambiguity a, String s){
    return a.<error descr="Ambiguous method call: both 'Ambiguity.executeServerOperation(ThrowableComputable<Boolean, IOException>)' and 'Ambiguity.executeServerOperation(Computable<Boolean>)' match">executeServerOperation</error>(() -> bool(s, a));
  }

  protected abstract boolean bool(String s, Ambiguity a);
}

interface ThrowableComputable<T, E extends Throwable> {
  T compute() throws E;
}
interface Computable <T> {

  T compute();
}
