// "Suppress for method" "true"

class Main {
  final OpenValueBased vb = new OpenValueBased();

  void f(){
    new OpenValueBased() {
      @SuppressWarnings("synchronization")
      @Override
      void g() {
        synchronized (vb){ }
      }
    };
  }
}

@jdk.internal.ValueBased
interface OpenValueBased {
  void g();
}
