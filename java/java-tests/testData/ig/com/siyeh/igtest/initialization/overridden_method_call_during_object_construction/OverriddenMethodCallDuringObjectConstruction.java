package initialization.overridden_method_call_during_object_construction;

public class OverriddenMethodCallDuringObjectConstruction extends Base {
  OverriddenMethodCallDuringObjectConstruction o;
  {
    try {
      o = (OverriddenMethodCallDuringObjectConstruction)<warning descr="Call to overridden method 'clone()' during object construction">clone</warning>();
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  OverriddenMethodCallDuringObjectConstruction() {
    <warning descr="Call to overridden method 'foo()' during object construction">foo</warning>();
  }

  @Override
  protected Object clone() throws CloneNotSupportedException {
    return super.clone();
  }
}
class Base implements Cloneable {
  public void foo() {}
}
class Overrider extends OverriddenMethodCallDuringObjectConstruction {
  public void foo() {
    System.out.println();
  }

  @Override
  protected Object clone() throws CloneNotSupportedException {
    return super.clone();
  }
}

