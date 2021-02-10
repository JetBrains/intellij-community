
import <warning descr="com.mycom.FirstPreviewFeature is a preview API and may be removed in a future release">com.mycom.FirstPreviewFeature</warning>;

class Main {
  {
    var a = <warning descr="com.mycom.FirstPreviewFeature.Outer#Inner is a preview API and may be removed in a future release">new FirstPreviewFeature.Outer.Inner()</warning>;
    <warning descr="com.mycom.FirstPreviewFeature.Outer.Inner#z is a preview API and may be removed in a future release">a.z</warning>();
    Runnable r = <warning descr="com.mycom.FirstPreviewFeature.Outer.Inner#z is a preview API and may be removed in a future release">a::z</warning>;
  }
}