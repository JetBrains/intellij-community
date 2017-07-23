class A {
  {
    try {
      mySocket.receive(p);
    }
    catch (<caret>SocketTimeoutException e) {
      throw new TimeoutOccurredException(e);
    }
  }
}