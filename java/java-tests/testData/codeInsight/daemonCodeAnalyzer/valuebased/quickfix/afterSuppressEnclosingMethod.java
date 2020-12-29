// "Suppress for method" "true"

class Main {
  final OpenValueBased vb = new OpenValueBased();

  @SuppressWarnings("synchronization")
  void f(){
    synchronized(vb){ }
  }
}

@jdk.internal.ValueBased
class OpenValueBased {}

