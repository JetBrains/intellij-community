// "Remove 'IOException' from 'foo' throws list" "false"
import java.io.*;

class Bar {

  public native int goDrink(String arg) throws IOExce<caret>ption;

  public static void main(String[] args) throws IOException {
    Bar bar = new Bar();
    bar.goDrink("aaa");
  }
}