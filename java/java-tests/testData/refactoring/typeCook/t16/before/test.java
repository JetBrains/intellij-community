class LinkedList<T>{
    T t;
    public T get(){return t;}
    public void set (T t){
	this.t = t;
    }    
}

class Simple {
   boolean f(){
    return false;
   }
}

class Test{
    LinkedList y;
    LinkedList x;
    
    void f(){
      x.set(y.get());
      Simple t = (Simple) y.get();
    }   
}
