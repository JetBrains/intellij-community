public class A {

    public void m<caret>(B b){
        System.out.println(this);
    }

    public void show(B b){
        m(b);
    }

    public void m (C c){
        System.out.println(this);
    }

}

class B{}
class C{}