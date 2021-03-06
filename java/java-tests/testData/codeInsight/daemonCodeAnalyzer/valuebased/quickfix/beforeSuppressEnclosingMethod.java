// "Suppress for method" "true"

class Main {
  final OpenValueBased vb = new OpenValueBased();

  void f(){
    synchronized(<caret>vb){ }
  }
}

@jdk.internal.ValueBased
class OpenValueBased {}

