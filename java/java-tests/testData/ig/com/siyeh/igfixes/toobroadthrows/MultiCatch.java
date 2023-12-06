class MultiCatch {

  public void x() throws ParentException<caret> {
    try {
      throw new ParentException();
    } catch (RuntimeException | ParentException e) {
      System.out.println(e);
      throw e;
    } catch (Exception e) {
      System.out.println(e);
      throw new ChildException();
    }
  }

  class ParentException extends Exception {}
  class ChildException extends ParentException {}
}