// "Suppress for method" "true"

class Main {
  final OpenValueBased vb = new OpenValueBased();

  void f(){
    new OpenValueBased() {
      @Override
      void g() {
        synchronized (<caret>vb){ }
      }
    };
  }
}

@jdk.internal.ValueBased
interface OpenValueBased {
  void g();
}
