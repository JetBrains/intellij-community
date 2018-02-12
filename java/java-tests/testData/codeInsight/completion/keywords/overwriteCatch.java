class A {
  {
    try {
      mySocket.receive(p);
    }
    <caret>catch (SocketTimeoutException e) {
      throw new TimeoutOccurredException(e);
    }
  }
}