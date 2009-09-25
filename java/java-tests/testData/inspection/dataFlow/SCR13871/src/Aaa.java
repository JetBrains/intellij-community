public class Aaa {
  Object getObject() {
    return null;
  }
  
  void f() {
    Object obj = getObject();
    if (obj instanceof Aaa || obj == null) {
      Aaa a = (Aaa) obj; // inspection reports that ClassCastException can be thrown
    }
  }
}