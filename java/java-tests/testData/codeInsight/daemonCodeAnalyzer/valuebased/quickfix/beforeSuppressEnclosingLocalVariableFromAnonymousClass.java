// "Suppress for statement" "true"

class Main {
  final OpenValueBased vb = new OpenValueBased();

  void f(){
    new OpenValueBased() {
      {
        synchronized (<caret>vb){ }
      }
    };
  }
}

@jdk.internal.ValueBased
interface OpenValueBased { }
