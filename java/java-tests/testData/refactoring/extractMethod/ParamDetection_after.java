import java.util.*;

class Test {
  public void foo() {
      List trades = newMethod();


      //probably not assigned !!!
    if (trades.isEmpty()) {
    }
  }

    private List newMethod() {
        List trades;
        try {
          trades = getTrades();
        }
        catch (RemoteException e) {
        }
        return trades;
    }

    static class RemoteException extends Exception {
  }
}
