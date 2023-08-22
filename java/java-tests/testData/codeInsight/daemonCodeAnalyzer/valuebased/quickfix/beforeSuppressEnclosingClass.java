// "Suppress for class" "true"

class Main {
  final OpenValueBased vb = new OpenValueBased();

  {
    synchronized(<caret>vb){ }
  }
}

@jdk.internal.ValueBased
class OpenValueBased {}

