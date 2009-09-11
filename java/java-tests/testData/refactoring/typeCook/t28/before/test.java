class LinkedList<T>{
    T t;
    T get(){return t;}
    void set(T t){}
}

class Simple {
}

class Test{
   LinkedList<Simple> x;
   LinkedList y = x;
}
