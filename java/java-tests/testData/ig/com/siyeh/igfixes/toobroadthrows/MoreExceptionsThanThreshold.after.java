class MoreExceptionsThanThreshold {
  public void xExpectedWarning(String x) throws Exception1, Exception2, Exception3, Exception4, Exception5 {
    if(x.equals("1")){
      throw new Exception1();
    }
    if(x.equals("2")){
      throw new Exception2();
    }
    if(x.equals("3")){
      throw new Exception3();
    }
    if(x.equals("4")){
      throw new Exception4();
    }
    if(x.equals("5")){
      throw new Exception5();
    }
  }

  public void xNotChanged(String x) throws ParentException {
    if(x.equals("1")){
      throw new Exception1();
    }
    if(x.equals("2")){
      throw new Exception2();
    }
    if(x.equals("3")){
      throw new Exception3();
    }
    if(x.equals("4")){
      throw new Exception4();
    }
    if(x.equals("5")){
      throw new Exception5();
    }
    if(x.equals("6")){
      throw new Exception6();
    }
  }

  class ParentException extends Exception {}
  class Exception1 extends ParentException {}
  class Exception2 extends ParentException {}
  class Exception3 extends ParentException {}
  class Exception4 extends ParentException {}
  class Exception5 extends ParentException {}
  class Exception6 extends ParentException {}
}