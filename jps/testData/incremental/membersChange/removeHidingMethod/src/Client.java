public class Client extends BaseClient{
  public void calculation() {
    new BaseAction() {
       public void action() {
          int data = getData(7);
       }
    }.action();
  }
}