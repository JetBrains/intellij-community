class Test{

  public int t() {
    Object[] <warning descr="Variable 'objects' can have 'final' modifier">objects</warning> = getObjects();
    for (int i = 0; i < objects.length; i++){
      Object <warning descr="Variable 'object' can have 'final' modifier">object</warning> = objects[i];
      return object.hashCode();
    }
    return 0;
  }

  native Object[] getObjects();
}