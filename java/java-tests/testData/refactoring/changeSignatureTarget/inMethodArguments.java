import java.util.*;
class B{

    public static void main(String[] args) {
      B b = null;
      b.bar(b.<Str<caret>ing>foo("", ""));
    }

    <T> String foo(T t, String s){return null;}
    String  bar(String s) {return null;}

}