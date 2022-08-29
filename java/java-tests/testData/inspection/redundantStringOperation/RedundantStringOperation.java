import java.io.ByteArrayOutputStream;

class Substring {

  void m(String message) {
    boolean underline = message.<warning descr="'substring()' call can be replaced with 'charAt()'">substring</warning>(1, 2).equals("_");
    String s1 = message.<warning descr="Call to 'substring()' is redundant">substring</warning>(message.length());
    StringBuilder sb = new StringBuilder();
    sb.append(message.<warning descr="'substring()' call can be replaced with 'charAt()'">substring</warning>(2, 3));
    if (!!!!!message.<warning descr="'substring()' call can be replaced with 'charAt()'">substring</warning>(4, 5).equals("_")) { }
    String s = new <warning descr="'new String()' is redundant">String</warning>("foo");
    String.valueOf(<warning descr="'new char[]' is redundant">new char[] </warning>{ 'c' });
    new <warning descr="Can be replaced with 'String.valueOf()'">String</warning>(new char[] { 'c' });
    String s2 = new <warning descr="'new String()' is redundant">String</warning>();

  }
}