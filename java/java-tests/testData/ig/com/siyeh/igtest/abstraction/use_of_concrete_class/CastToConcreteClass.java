package com.siyeh.igtest.abstraction.cast_to_concrete_class;

class CastToConcreteClass {

  private String field;

  @Override
  public boolean equals(Object obj) {
    try {
      CastToConcreteClass c = (CastToConcreteClass)obj;
      return c.field.equals(field);
    } catch (ClassCastException e) {
      return false;
    }
  }

  @Override
  public <warning descr="Method returns a concrete class 'CastToConcreteClass'">CastToConcreteClass</warning> clone() throws CloneNotSupportedException {
    return (CastToConcreteClass) super.clone();
  }

  void foo(Object o) {
    <warning descr="Local variable 'c' of concrete class 'CastToConcreteClass'">CastToConcreteClass</warning> c = (<warning descr="Cast to concrete class 'CastToConcreteClass'">CastToConcreteClass</warning>)o;
    <warning descr="Local variable 'c2' of concrete class 'CastToConcreteClass'">CastToConcreteClass</warning> c2 = CastToConcreteClass.class.<warning descr="Cast to concrete class 'CastToConcreteClass'">cast</warning>(o);
    final Class<CastToConcreteClass> aClass = CastToConcreteClass.class;
    final <warning descr="Local variable 'c3' of concrete class 'CastToConcreteClass'">CastToConcreteClass</warning> c3 = aClass.<warning descr="Cast to concrete class 'CastToConcreteClass'">cast</warning>(o);
  }
}