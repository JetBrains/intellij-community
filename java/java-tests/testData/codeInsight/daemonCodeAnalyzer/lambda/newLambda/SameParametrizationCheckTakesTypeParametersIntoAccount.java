import java.util.Optional;

interface Work<T> {
  T execute();
}

interface Result {}

class ResultImpl implements Result {}

class Provider {
  <T> T doWork(Work<T> work) {
    return work.execute();
  }
}

class Main {
  public static void main(String[] args) {
    final Provider provider = new Provider();
    final Optional<Result> result = provider.doWork(() -> {
      if (args.length > 1) {
        return Optional.of(new ResultImpl());
      } else {
        return Optional.of(new ResultImpl());
      }
    });
  }
}