import java.io.*;

class Bar {

  public native int goDrink(String arg) throws IOException;

  public static void main(String[] args) throws IOException {
    Bar bar = new Bar();
    bar.goDrink("aaa");
  }
}