class NewName {
  interface I {
    Class[] m(int i);
  }

  {
    final I aNew = Class[]::new;
  }
}