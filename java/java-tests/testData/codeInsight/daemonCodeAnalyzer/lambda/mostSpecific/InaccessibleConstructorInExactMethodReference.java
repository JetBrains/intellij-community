import java.util.function.Function;
import java.util.function.Supplier;

interface ThrowingFunction<X, Y> { Y apply(X value) throws Exception; }
interface ThrowingSupplier<X> { X get() throws Exception; }

class Adapters {
  static <X, Y> Function<X, Y> adapt(ThrowingFunction<X, Y> f) { return null; }
  static <X> Supplier<X> adapt(ThrowingSupplier<X> s) { return null; }
}

class Client {
  Client() throws Exception {}
  private Client(boolean mode) {}
}

class Use {
  Supplier<Client> m() {
    return Adapters.adapt(Client::new);
  }
}
