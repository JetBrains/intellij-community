// "Suppress for method" "true"

class Main {
  final OpenValueBased vb = new OpenValueBased(){
    @Override
    public void g() {
    }
  };

  void f(){
    new OpenValueBased() {
      @SuppressWarnings("synchronization")
      @Override
      public void g() {
        synchronized (vb){ }
      }
    };
  }
}

@jdk.internal.ValueBased
interface OpenValueBased {
  void g();
}
