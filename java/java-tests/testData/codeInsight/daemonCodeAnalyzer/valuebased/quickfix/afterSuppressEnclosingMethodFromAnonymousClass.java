// "Suppress for method" "true"

class Main {
  final OpenValueBased vb = new OpenValueBased();

  @SuppressWarnings("synchronization")
  void f(){
    new OpenValueBased() {
      {
        synchronized (vb){ }
      }
    };
  }
}

@jdk.internal.ValueBased
interface OpenValueBased { }
