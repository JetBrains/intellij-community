@com.google.common.annotations.VisibleForTesting
public class RelaxedForTesting {
  RelaxedForTesting() {
  }
}

class Client {
  {
    new RelaxedForTesting();
  }
}