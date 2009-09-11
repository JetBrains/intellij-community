package pack1;

public class Client { 
    public static void main(String[] args) {
        StaticInner staticInner = new StaticInner();
 
        StaticInner.NonStaticInnerInner nonStaticInnerInner
            = staticInner.new NonStaticInnerInner("Joe");
 
        System.out.println(nonStaticInnerInner.toString());
    }
}