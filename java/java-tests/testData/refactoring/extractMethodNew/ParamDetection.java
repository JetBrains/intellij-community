import java.util.*;

class Test {
  public void foo() {
    <selection>List trades;
    try {
      trades = getTrades();
    }
    catch (RemoteException e) {
    }
    </selection>

    //probably not assigned !!!
    if (trades.isEmpty()) {
    }
  }

  static class RemoteException extends Exception {
  }
}
