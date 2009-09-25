import java.rmi.*;

interface A extends Remote {
  void a() throws RemoteException;
}


class D implements A {
  public void a() {
  }
}


interface NotRemotable {
  void a() throws RemoteException;
}


class DNotRemotable implements NotRemotable {
  public void a() {
  }
}


