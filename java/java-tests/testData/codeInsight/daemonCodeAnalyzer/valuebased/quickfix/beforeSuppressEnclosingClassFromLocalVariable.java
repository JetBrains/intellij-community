// "Suppress for class" "true"

class Main {
  final OpenValueBased vb = new OpenValueBased();

  void f(){
    var l = ((new OpenValueBased() {
      {
        synchronized (<caret>vb){ }
      }
    }));
  }
}

@jdk.internal.ValueBased
interface OpenValueBased { }
