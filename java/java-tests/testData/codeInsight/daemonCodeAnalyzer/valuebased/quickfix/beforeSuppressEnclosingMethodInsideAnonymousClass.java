// "Suppress for method" "true"

class Main {
  final OpenValueBased vb = new OpenValueBased(){
    @Override
    public void g() {
    }
  };

  void f(){
    new OpenValueBased() {
      @Override
      public void g() {
        synchronized (<caret>vb){ }
      }
    };
  }
}

@jdk.internal.ValueBased
interface OpenValueBased {
  void g();
}
