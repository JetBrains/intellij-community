// "Suppress for class" "true"

@SuppressWarnings("synchronization")
class Main {
  final OpenValueBased vb = new OpenValueBased();

  {
    synchronized(vb){ }
  }
}

@jdk.internal.ValueBased
class OpenValueBased {}

