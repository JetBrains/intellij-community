class Test  {

  {
    addListener(this::editPropertyChanged);
  }

  void addListener(ChangeListener<? super Boolean> changeListener){}

  public void editPropertyChanged(Value<? extends Boolean> property) {}


  interface ChangeListener <T> {
    void changed(Value<? extends T> value);
  }

  class Value<K> {}

}