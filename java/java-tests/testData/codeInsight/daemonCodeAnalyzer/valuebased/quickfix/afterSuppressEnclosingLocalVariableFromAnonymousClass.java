// "Suppress for statement" "true"

class Main {
  final OpenValueBased vb = new OpenValueBased();

  void f(){
    new OpenValueBased() {
      {
          //noinspection synchronization
          synchronized (vb){ }
      }
    };
  }
}

@jdk.internal.ValueBased
interface OpenValueBased { }
