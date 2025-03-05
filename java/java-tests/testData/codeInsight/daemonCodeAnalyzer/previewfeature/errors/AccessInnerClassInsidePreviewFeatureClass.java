
import <error descr="com.mycom.FirstPreviewFeature is a preview API and is disabled by default">com.mycom.FirstPreviewFeature</error>;
import com.mycom.FirstPreviewFeatureReflective;

class Main {
  public Main(String value) { }
  {
    var a = new <error descr="com.mycom.FirstPreviewFeature is a preview API and is disabled by default">FirstPreviewFeature</error>.Outer.Inner();
    var b = new <warning descr="com.mycom.FirstPreviewFeatureReflective.Outer.Inner is a reflective preview API and may be removed in a future release"><warning descr="com.mycom.FirstPreviewFeatureReflective.Outer is a reflective preview API and may be removed in a future release"><warning descr="com.mycom.FirstPreviewFeatureReflective is a reflective preview API and may be removed in a future release">FirstPreviewFeatureReflective</warning>.Outer</warning>.Inner</warning>();
    <error descr="com.mycom.FirstPreviewFeature.Outer.Inner#z is a preview API and is disabled by default">a.z</error>();
    b.<warning descr="com.mycom.FirstPreviewFeatureReflective.Outer.Inner#z is a reflective preview API and may be removed in a future release">z</warning>();
    Runnable r1 = <error descr="com.mycom.FirstPreviewFeature.Outer.Inner#z is a preview API and is disabled by default">a::z</error>;
    Runnable r2 = b::<warning descr="com.mycom.FirstPreviewFeatureReflective.Outer.Inner#z is a reflective preview API and may be removed in a future release">z</warning>;
    new Main(<error descr="com.mycom.FirstPreviewFeature is a preview API and is disabled by default">FirstPreviewFeature</error>.KEY);
    new Main(<warning descr="com.mycom.FirstPreviewFeatureReflective is a reflective preview API and may be removed in a future release">FirstPreviewFeatureReflective</warning>.<warning descr="com.mycom.FirstPreviewFeatureReflective#KEY is a reflective preview API and may be removed in a future release">KEY</warning>);

    new Main(<error descr="com.mycom.FirstPreviewFeature is a preview API and is disabled by default">FirstPreviewFeature</error>.KEY + "");
    new Main(<warning descr="com.mycom.FirstPreviewFeatureReflective is a reflective preview API and may be removed in a future release">FirstPreviewFeatureReflective</warning>.<warning descr="com.mycom.FirstPreviewFeatureReflective#KEY is a reflective preview API and may be removed in a future release">KEY</warning> + "");

    new Main("" + <error descr="com.mycom.FirstPreviewFeature is a preview API and is disabled by default">FirstPreviewFeature</error>.KEY);
    new Main("" + <warning descr="com.mycom.FirstPreviewFeatureReflective is a reflective preview API and may be removed in a future release">FirstPreviewFeatureReflective</warning>.<warning descr="com.mycom.FirstPreviewFeatureReflective#KEY is a reflective preview API and may be removed in a future release">KEY</warning>);
  }
}