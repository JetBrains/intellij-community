class A{
  private A myClassCombo;
  protected void createNorthPanel() {
    ((A)myClassCombo<caret>)
  }
}
