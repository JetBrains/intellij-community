class Namedf {
  public <T extends AnInterface & AnotherInterface> void thing(T aThing) {
    assert <warning descr="Condition 'aThing == null || aThing instanceof AnInterface' is always 'true'">aThing == null || <warning descr="Condition 'aThing instanceof AnInterface' is always 'true' when reached">aThing instanceof AnInterface</warning></warning>;
    assert <warning descr="Condition 'aThing == null || aThing instanceof AnotherInterface' is always 'true'">aThing == null || <warning descr="Condition 'aThing instanceof AnotherInterface' is always 'true' when reached">aThing instanceof AnotherInterface</warning></warning>;
  }

  interface AnInterface {}
  interface AnotherInterface {}
}
