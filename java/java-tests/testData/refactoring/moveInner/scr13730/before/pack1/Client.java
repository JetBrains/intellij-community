package pack1;

public class Client { 
    public static void main(String[] args) {
        TopLevel.StaticInner staticInner = new TopLevel.StaticInner();
 
        TopLevel.StaticInner.NonStaticInnerInner nonStaticInnerInner
            = staticInner.new NonStaticInnerInner("Joe");
 
        System.out.println(nonStaticInnerInner.toString());
    }
}