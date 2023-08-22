// "Suppress for class" "true"

@SuppressWarnings("synchronization")
class Main {
  final OpenValueBased vb = new OpenValueBased();

  void f(){
    var l = ((new OpenValueBased() {
      {
        synchronized (vb){ }
      }
    }));
  }
}

@jdk.internal.ValueBased
interface OpenValueBased { }
