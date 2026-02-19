package ppp;

public class Main {
  public static void main(String[] args) {
    Repository repo = new Repository();
    Client client = new Client();
    client.execute(repo::getStorages);
  }
}
