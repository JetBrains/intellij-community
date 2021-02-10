
import <error descr="com.mycom.FirstPreviewFeature is a preview API and may be removed in a future release">com.mycom.FirstPreviewFeature</error>;
import <warning descr="com.mycom.FirstPreviewFeatureReflective is a preview API and may be removed in a future release">com.mycom.FirstPreviewFeatureReflective</warning>;

class Main {
  {
    var a = <error descr="com.mycom.FirstPreviewFeature.Outer#Inner is a preview API and may be removed in a future release">new FirstPreviewFeature.Outer.Inner()</error>;
    var b = <warning descr="com.mycom.FirstPreviewFeatureReflective.Outer#Inner is a preview API and may be removed in a future release">new FirstPreviewFeatureReflective.Outer.Inner()</warning>;
    <error descr="com.mycom.FirstPreviewFeature.Outer.Inner#z is a preview API and may be removed in a future release">a.z</error>();
    <warning descr="com.mycom.FirstPreviewFeatureReflective.Outer.Inner#z is a preview API and may be removed in a future release">b.z</warning>();
    Runnable r1 = <error descr="com.mycom.FirstPreviewFeature.Outer.Inner#z is a preview API and may be removed in a future release">a::z</error>;
    Runnable r2 = <warning descr="com.mycom.FirstPreviewFeatureReflective.Outer.Inner#z is a preview API and may be removed in a future release">b::z</warning>;
  }
}