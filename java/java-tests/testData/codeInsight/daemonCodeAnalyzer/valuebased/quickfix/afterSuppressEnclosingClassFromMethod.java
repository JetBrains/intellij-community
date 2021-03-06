// "Suppress for class" "true"

@SuppressWarnings("synchronization")
class Main {
  final OpenValueBased vb = new OpenValueBased();

  void f{
    synchronized(vb){ }
  }
}

@jdk.internal.ValueBased
class OpenValueBased {}

