public class <caret>AGeneric<T> {
    private T myT;
    public T getT() {
     	return myT;
    }
    public void setT(T t) {
        myT = t;
    }
}

class Client {
  private AGeneric<String> myGen = new AGeneric<String>();
}
