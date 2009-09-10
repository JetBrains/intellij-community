import java.util.*;

class A {
    private AGeneric<Collection> b = new AGeneric<Collection>();

    private class <caret>AGeneric<T> {
        private T myV;

        public <X extends T> void parMethA(X p) {
             myV = p;
        }

        private class Inner<Y extends T> {
    	    private Y myY;
        }
    }
}