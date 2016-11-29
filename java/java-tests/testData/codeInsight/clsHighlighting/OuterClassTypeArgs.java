
import test.InnerClassTypeMultipleGeneric;

class Main {
  public static void main(String[] args) {
    InnerClassTypeMultipleGeneric.Outer<Character, Boolean>.Inner<Byte>
      byteInner = InnerClassTypeMultipleGeneric.staticType();
  }
}