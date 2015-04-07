package ppp;
public class Client {
  public void execute(DataProvider data) {
    for (String s : data.getData()) {
      System.out.println(s);
    }
  }
}
